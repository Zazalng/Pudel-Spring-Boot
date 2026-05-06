/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
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
package group.worldstandard.pudel.core.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Service for validating DPoP proofs in BFF-style architecture.
 * <p>
 * In BFF style, the backend holds the private key and signs DPoP proofs for the frontend.
 * This service validates that the DPoP proof was signed by the backend's session key.
 * <p>
 * Validation steps:
 * 1. Parse the DPoP proof JWT
 * 2. Extract the JWK from the header
 * 3. Verify the signature using the public key from the session
 * 4. Validate claims (htm, htu, iat, jti, ath)
 */
@Service
public class DPoPService {
    private static final Logger log = LoggerFactory.getLogger(DPoPService.class);

    // Session attribute name for storing the keypair (must match DPoPKeyManager)
    private static final String SESSION_KEY_PAIR = "DPoPKeyManager.KeyPair";
    private static final String SESSION_PUBLIC_KEY_JWK = "DPoPKeyManager.PublicKeyJwk";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Result of DPoP proof validation.
     */
    public record DPoPValidationResult(boolean valid, String error, String thumbprint) {
        public static DPoPValidationResult valid(String thumbprint) {
            return new DPoPValidationResult(true, null, thumbprint);
        }

        public static DPoPValidationResult invalid(String error) {
            return new DPoPValidationResult(false, error, null);
        }
    }

    /**
     * Validate a DPoP proof for a resource request (BFF-style).
     *
     * @param dpopProof The DPoP proof JWT from the DPoP header
     * @param httpMethod The HTTP method of the request
     * @param httpUri The full URI of the request
     * @param accessToken The access token (for ath validation)
     * @param session The HTTP session (contains the public key for verification)
     * @return Validation result
     */
    public DPoPValidationResult validateProofForResource(String dpopProof,
                                                         String httpMethod,
                                                         String httpUri,
                                                         String accessToken,
                                                         HttpSession session) {
        try {
            // Get the public key from session
            @SuppressWarnings("unchecked")
            Map<String, Object> sessionJwk = (Map<String, Object>) session.getAttribute(SESSION_PUBLIC_KEY_JWK);
            if (sessionJwk == null) {
                return DPoPValidationResult.invalid("No DPoP key found in session");
            }

            // Get the keypair from session for signature verification
            java.security.KeyPair keyPair = (java.security.KeyPair) session.getAttribute(SESSION_KEY_PAIR);
            if (keyPair == null) {
                return DPoPValidationResult.invalid("No DPoP key pair in session");
            }

            // Verify the JWT signature and parse claims
            Claims claims;
            try {
                claims = Jwts.parser()
                        .verifyWith(keyPair.getPublic())
                        .build()
                        .parseSignedClaims(dpopProof)
                        .getPayload();
            } catch (Exception e) {
                return DPoPValidationResult.invalid("Invalid DPoP proof signature");
            }

            // Validate htm (HTTP method)
            String htm = claims.get("htm", String.class);
            if (htm == null || !htm.equalsIgnoreCase(httpMethod)) {
                return DPoPValidationResult.invalid("Invalid htm claim");
            }

            // Validate htu (HTTP URI) - normalize both for comparison
            String htu = claims.get("htu", String.class);
            if (htu == null || !normalizeUri(htu).equals(normalizeUri(httpUri))) {
                return DPoPValidationResult.invalid("Invalid htu claim");
            }

            // Validate iat (issued at) - must be recent (within 60 seconds)
            Date iat = claims.getIssuedAt();
            if (iat == null) {
                return DPoPValidationResult.invalid("Missing iat claim");
            }
            long now = System.currentTimeMillis();
            long iatMillis = iat.getTime();
            if (Math.abs(now - iatMillis) > 60000) { // 60 seconds tolerance
                return DPoPValidationResult.invalid("iat too old or in future");
            }

            // Validate jti (unique identifier)
            String jti = claims.getId();
            if (jti == null || jti.isEmpty()) {
                return DPoPValidationResult.invalid("Missing jti claim");
            }

            // Validate ath (access token hash) if access token provided
            if (accessToken != null) {
                String ath = claims.get("ath", String.class);
                if (ath == null) {
                    return DPoPValidationResult.invalid("Missing ath claim for DPoP-bound token");
                }
                String expectedAth = calculateAccessTokenHash(accessToken);
                if (!expectedAth.equals(ath)) {
                    return DPoPValidationResult.invalid("Invalid ath claim");
                }
            }

            // Return the thumbprint for binding verification
            String sessionThumbprint = calculateJwkThumbprint(sessionJwk);
            return DPoPValidationResult.valid(sessionThumbprint);

        } catch (Exception e) {
            log.warn("DPoP proof validation error", e);
            return DPoPValidationResult.invalid("Invalid DPoP proof: " + e.getMessage());
        }
    }

    /**
     * Calculate SHA-256 hash of access token (base64url encoded) per RFC 9449.
     */
    private String calculateAccessTokenHash(String accessToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Normalize URI for comparison (remove query string and fragment).
     */
    private String normalizeUri(String uri) {
        try {
            java.net.URI parsed = new java.net.URI(uri);
            return parsed.getScheme() + "://" + parsed.getHost() + parsed.getPath();
        } catch (Exception e) {
            return uri;
        }
    }

    /**
     * Calculate JWK thumbprint per RFC 7638 using Jackson ObjectMapper.
     */
    private String calculateJwkThumbprint(Map<String, Object> jwk) {
        try {
            String kty = (String) jwk.get("kty");
            ObjectNode json = objectMapper.createObjectNode();

            if ("RSA".equals(kty)) {
                json.put("e", (String) jwk.get("e"));
                json.put("kty", "RSA");
                json.put("n", (String) jwk.get("n"));
            } else if ("EC".equals(kty)) {
                json.put("crv", (String) jwk.get("crv"));
                json.put("kty", "EC");
                json.put("x", (String) jwk.get("x"));
                json.put("y", (String) jwk.get("y"));
            } else {
                throw new IllegalArgumentException("Unsupported key type: " + kty);
            }

            // Convert to JSON string with sorted keys for canonical form
            String jsonString = objectMapper.writeValueAsString(json);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate JWK thumbprint", e);
        }
    }
}