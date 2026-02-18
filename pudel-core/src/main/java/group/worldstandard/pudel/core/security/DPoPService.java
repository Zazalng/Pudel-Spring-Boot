/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * DPoP (Demonstrating Proof-of-Possession) Service.
 * <p>
 * Implements RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP).
 * <p>
 * DPoP prevents token theft by binding tokens to a specific client key pair:
 * <ul>
 *   <li>Client generates an asymmetric key pair</li>
 *   <li>Client includes a DPoP proof (signed JWT) with each request</li>
 *   <li>Server validates the proof and binds the access token to the client's public key</li>
 *   <li>Even if the access token is stolen, it cannot be used without the private key</li>
 * </ul>
 * <p>
 * DPoP Proof JWT Structure:
 * <pre>
 * Header: {
 *   "typ": "dpop+jwt",
 *   "alg": "RS256" or "ES256",
 *   "jwk": { client's public key }
 * }
 * Payload: {
 *   "jti": unique identifier,
 *   "htm": HTTP method,
 *   "htu": HTTP URI,
 *   "iat": issued at timestamp,
 *   "ath": access token hash (SHA-256, base64url) - only for protected resources
 * }
 * </pre>
 */
@Component
public class DPoPService {

    private static final Logger log = LoggerFactory.getLogger(DPoPService.class);

    // Maximum age for DPoP proofs (5 minutes)
    private static final long MAX_PROOF_AGE_MS = 5 * 60 * 1000;

    // Clock skew tolerance (30 seconds)
    private static final long CLOCK_SKEW_MS = 30 * 1000;

    // Used JTI (JWT ID) cache to prevent replay attacks
    // Key: jti, Value: expiry timestamp
    private final Map<String, Long> usedJtis = new ConcurrentHashMap<>();

    // Token thumbprint bindings: accessToken -> jwkThumbprint
    private final Map<String, String> tokenBindings = new ConcurrentHashMap<>();

    public DPoPService() {
        // Start cleanup thread for expired JTIs
        Thread cleanupThread = new Thread(this::cleanupExpiredJtis, "dpop-jti-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * Validate a DPoP proof for token request (no ath claim required).
     *
     * @param dpopProof the DPoP proof JWT from DPoP header
     * @param httpMethod the HTTP method of the request
     * @param httpUri the HTTP URI of the request
     * @return DPoPValidationResult with thumbprint if valid
     */
    public DPoPValidationResult validateProofForTokenRequest(String dpopProof, String httpMethod, String httpUri) {
        return validateProof(dpopProof, httpMethod, httpUri, null);
    }

    /**
     * Validate a DPoP proof for protected resource access.
     *
     * @param dpopProof the DPoP proof JWT from DPoP header
     * @param httpMethod the HTTP method of the request
     * @param httpUri the HTTP URI of the request
     * @param accessToken the access token (for ath validation)
     * @return DPoPValidationResult with thumbprint if valid
     */
    public DPoPValidationResult validateProofForResource(String dpopProof, String httpMethod, String httpUri, String accessToken) {
        return validateProof(dpopProof, httpMethod, httpUri, accessToken);
    }

    /**
     * Core DPoP proof validation logic.
     */
    private DPoPValidationResult validateProof(String dpopProof, String httpMethod, String httpUri, String accessToken) {
        if (dpopProof == null || dpopProof.isBlank()) {
            return DPoPValidationResult.failure("Missing DPoP proof");
        }

        try {
            // Split JWT parts
            String[] parts = dpopProof.split("\\.");
            if (parts.length != 3) {
                return DPoPValidationResult.failure("Invalid DPoP proof format");
            }

            // Decode header
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Map<String, Object> header = parseJson(headerJson);

            // Validate typ
            String typ = (String) header.get("typ");
            if (!"dpop+jwt".equals(typ)) {
                return DPoPValidationResult.failure("Invalid typ: expected dpop+jwt");
            }

            // Extract JWK from header
            @SuppressWarnings("unchecked")
            Map<String, Object> jwk = (Map<String, Object>) header.get("jwk");
            if (jwk == null) {
                return DPoPValidationResult.failure("Missing jwk in header");
            }

            // Convert JWK to PublicKey
            PublicKey publicKey = jwkToPublicKey(jwk);
            if (publicKey == null) {
                return DPoPValidationResult.failure("Invalid JWK");
            }

            // Calculate JWK thumbprint
            String thumbprint = calculateJwkThumbprint(jwk);

            // Verify signature
            Claims claims;
            try {
                claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(dpopProof)
                        .getPayload();
            } catch (SignatureException e) {
                return DPoPValidationResult.failure("Invalid signature");
            } catch (ExpiredJwtException e) {
                return DPoPValidationResult.failure("Proof expired");
            }

            // Validate jti (unique identifier)
            String jti = claims.get("jti", String.class);
            if (jti == null || jti.isBlank()) {
                return DPoPValidationResult.failure("Missing jti claim");
            }

            // Check for replay attack
            if (isJtiUsed(jti)) {
                log.warn("DPoP replay attack detected: jti={}", jti);
                return DPoPValidationResult.failure("Proof already used (replay detected)");
            }

            // Validate htm (HTTP method)
            String htm = claims.get("htm", String.class);
            if (!httpMethod.equalsIgnoreCase(htm)) {
                return DPoPValidationResult.failure("HTTP method mismatch: expected " + httpMethod + ", got " + htm);
            }

            // Validate htu (HTTP URI)
            String htu = claims.get("htu", String.class);
            if (!normalizeUri(httpUri).equals(normalizeUri(htu))) {
                return DPoPValidationResult.failure("HTTP URI mismatch");
            }

            // Validate iat (issued at)
            Date iat = claims.getIssuedAt();
            if (iat == null) {
                return DPoPValidationResult.failure("Missing iat claim");
            }

            long now = System.currentTimeMillis();
            long iatTime = iat.getTime();

            // Check if proof is too old
            if (now - iatTime > MAX_PROOF_AGE_MS) {
                return DPoPValidationResult.failure("Proof too old");
            }

            // Check if proof is from the future (with clock skew tolerance)
            if (iatTime - now > CLOCK_SKEW_MS) {
                return DPoPValidationResult.failure("Proof issued in the future");
            }

            // Validate ath (access token hash) if access token provided
            if (accessToken != null) {
                String ath = claims.get("ath", String.class);
                if (ath == null) {
                    return DPoPValidationResult.failure("Missing ath claim for protected resource");
                }

                String expectedAth = calculateAccessTokenHash(accessToken);
                if (!expectedAth.equals(ath)) {
                    return DPoPValidationResult.failure("Access token hash mismatch");
                }

                // Verify token is bound to this thumbprint
                String boundThumbprint = tokenBindings.get(accessToken);
                if (boundThumbprint != null && !boundThumbprint.equals(thumbprint)) {
                    log.warn("DPoP binding mismatch: token bound to {} but proof from {}", boundThumbprint, thumbprint);
                    return DPoPValidationResult.failure("Token not bound to this key");
                }
            }

            // Mark JTI as used
            markJtiUsed(jti);

            log.debug("DPoP proof validated successfully: thumbprint={}", thumbprint);
            return DPoPValidationResult.success(thumbprint, jwk);

        } catch (Exception e) {
            log.error("DPoP validation error: {}", e.getMessage());
            return DPoPValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Bind an access token to a JWK thumbprint.
     * Called when issuing a token with DPoP.
     */
    public void bindTokenToThumbprint(String accessToken, String thumbprint) {
        tokenBindings.put(accessToken, thumbprint);
        log.debug("Bound token to thumbprint: {}", thumbprint);
    }

    /**
     * Check if an access token is bound to a specific thumbprint.
     */
    public boolean isTokenBoundTo(String accessToken, String thumbprint) {
        String bound = tokenBindings.get(accessToken);
        return bound != null && bound.equals(thumbprint);
    }

    /**
     * Revoke a token binding (e.g., on logout).
     */
    public void revokeTokenBinding(String accessToken) {
        tokenBindings.remove(accessToken);
    }

    /**
     * Get the thumbprint bound to a token.
     */
    public String getBoundThumbprint(String accessToken) {
        return tokenBindings.get(accessToken);
    }

    /**
     * Calculate SHA-256 hash of access token (base64url encoded).
     */
    private String calculateAccessTokenHash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Calculate JWK thumbprint per RFC 7638.
     */
    private String calculateJwkThumbprint(Map<String, Object> jwk) {
        try {
            // Build canonical JSON (sorted keys)
            String kty = (String) jwk.get("kty");
            StringBuilder json = new StringBuilder();

            if ("RSA".equals(kty)) {
                json.append("{\"e\":\"").append(jwk.get("e")).append("\",");
                json.append("\"kty\":\"RSA\",");
                json.append("\"n\":\"").append(jwk.get("n")).append("\"}");
            } else if ("EC".equals(kty)) {
                json.append("{\"crv\":\"").append(jwk.get("crv")).append("\",");
                json.append("\"kty\":\"EC\",");
                json.append("\"x\":\"").append(jwk.get("x")).append("\",");
                json.append("\"y\":\"").append(jwk.get("y")).append("\"}");
            } else {
                throw new IllegalArgumentException("Unsupported key type: " + kty);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate JWK thumbprint", e);
        }
    }

    /**
     * Convert JWK to PublicKey.
     */
    private PublicKey jwkToPublicKey(Map<String, Object> jwk) {
        try {
            String kty = (String) jwk.get("kty");

            if ("RSA".equals(kty)) {
                String n = (String) jwk.get("n");
                String e = (String) jwk.get("e");

                byte[] nBytes = Base64.getUrlDecoder().decode(n);
                byte[] eBytes = Base64.getUrlDecoder().decode(e);

                java.math.BigInteger modulus = new java.math.BigInteger(1, nBytes);
                java.math.BigInteger exponent = new java.math.BigInteger(1, eBytes);

                java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(spec);

            } else if ("EC".equals(kty)) {
                String crv = (String) jwk.get("crv");
                String x = (String) jwk.get("x");
                String y = (String) jwk.get("y");

                byte[] xBytes = Base64.getUrlDecoder().decode(x);
                byte[] yBytes = Base64.getUrlDecoder().decode(y);

                java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                        new java.math.BigInteger(1, xBytes),
                        new java.math.BigInteger(1, yBytes)
                );

                java.security.spec.ECGenParameterSpec ecSpec = new java.security.spec.ECGenParameterSpec(
                        switch (crv) {
                            case "P-256" -> "secp256r1";
                            case "P-384" -> "secp384r1";
                            case "P-521" -> "secp521r1";
                            default -> throw new IllegalArgumentException("Unsupported curve: " + crv);
                        }
                );

                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                kpg.initialize(ecSpec);
                java.security.interfaces.ECPublicKey tempKey =
                        (java.security.interfaces.ECPublicKey) kpg.generateKeyPair().getPublic();

                java.security.spec.ECPublicKeySpec pubSpec =
                        new java.security.spec.ECPublicKeySpec(point, tempKey.getParams());
                KeyFactory kf = KeyFactory.getInstance("EC");
                return kf.generatePublic(pubSpec);
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to convert JWK to PublicKey: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalize URI for comparison.
     */
    private String normalizeUri(String uri) {
        if (uri == null) return "";
        // Remove query string and fragment
        int queryIdx = uri.indexOf('?');
        if (queryIdx > 0) {
            uri = uri.substring(0, queryIdx);
        }
        int fragIdx = uri.indexOf('#');
        if (fragIdx > 0) {
            uri = uri.substring(0, fragIdx);
        }
        // Ensure no trailing slash
        while (uri.endsWith("/") && uri.length() > 1) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.toLowerCase();
    }

    /**
     * Parse JSON string to map (simple implementation).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws Exception {
        // Use a simple JSON parser approach
        // In production, you'd use Jackson or Gson
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(json, Map.class);
    }

    /**
     * Check if a JTI has been used (replay detection).
     */
    private boolean isJtiUsed(String jti) {
        Long expiry = usedJtis.get(jti);
        if (expiry == null) {
            return false;
        }
        // Check if still within replay window
        return System.currentTimeMillis() < expiry;
    }

    /**
     * Mark a JTI as used.
     */
    private void markJtiUsed(String jti) {
        // Store with expiry = now + max proof age + some buffer
        long expiry = System.currentTimeMillis() + MAX_PROOF_AGE_MS + CLOCK_SKEW_MS;
        usedJtis.put(jti, expiry);
    }

    /**
     * Cleanup expired JTIs periodically.
     */
    private void cleanupExpiredJtis() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TimeUnit.MINUTES.sleep(5);
                long now = System.currentTimeMillis();
                usedJtis.entrySet().removeIf(entry -> entry.getValue() < now);

                // Also cleanup old token bindings (tokens older than 1 day)
                // In production, you'd want to track actual token expiry
                log.debug("DPoP cleanup: {} JTIs, {} token bindings", usedJtis.size(), tokenBindings.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Result of DPoP validation.
     */
    public record DPoPValidationResult(
            boolean valid,
            String error,
            String thumbprint,
            Map<String, Object> jwk
    ) {
        public static DPoPValidationResult success(String thumbprint, Map<String, Object> jwk) {
            return new DPoPValidationResult(true, null, thumbprint, jwk);
        }

        public static DPoPValidationResult failure(String error) {
            return new DPoPValidationResult(false, error, null, null);
        }
    }
}

