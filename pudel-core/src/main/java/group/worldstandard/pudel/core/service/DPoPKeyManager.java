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

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * BFF-style DPoP key management service.
 * <p>
 * In this approach, the backend generates and manages the DPoP keypair for each user session.
 * The private key never leaves the backend, providing enhanced security against XSS attacks.
 * <p>
 * Flow:
 * 1. Backend generates DPoP keypair for user session (stored in HttpSession)
 * 2. Frontend requests public key from backend
 * 3. Frontend creates DPoP proof payload (jti, htm, htu, iat, ath)
 * 4. Frontend sends payload to backend for signing
 * 5. Backend signs payload with stored private key and returns signed proof
 * 6. Frontend uses signed proof in DPoP header
 */
@Component
public class DPoPKeyManager {
    private static final Logger log = LoggerFactory.getLogger(DPoPKeyManager.class);

    // Session attribute name for storing the keypair
    private static final String SESSION_KEY_PAIR = "DPoPKeyManager.KeyPair";
    private static final String SESSION_PUBLIC_KEY_JWK = "DPoPKeyManager.PublicKeyJwk";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize or retrieve the DPoP keypair for this session.
     * Generates a new keypair if one doesn't exist yet.
     * Uses HttpSession to store the keypair.
     */
    public void initializeKeyPair(jakarta.servlet.http.HttpSession session) {
        if (session.getAttribute(SESSION_KEY_PAIR) != null) {
            return;
        }

        try {
            // Generate EC key pair for DPoP (P-256 curve)
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            keyPairGenerator.initialize(ecSpec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Store in session
            session.setAttribute(SESSION_KEY_PAIR, keyPair);

            // Also cache the JWK in session
            Map<String, Object> jwk = convertPublicKeyToJwk((ECPublicKey) keyPair.getPublic());
            session.setAttribute(SESSION_PUBLIC_KEY_JWK, jwk);

            log.debug("Generated new DPoP keypair for session: {}", jwk.get("x"));
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            log.error("Failed to generate DPoP keypair", e);
            throw new RuntimeException("Failed to initialize DPoP keypair", e);
        }
    }

    /**
     * Get the DPoP public key in JWK format from session.
     * @param session The HTTP session
     * @return Public key as JWK map
     */
    public Map<String, Object> getPublicKeyJwk(jakarta.servlet.http.HttpSession session) {
        initializeKeyPair(session);

        @SuppressWarnings("unchecked")
        Map<String, Object> jwk = (Map<String, Object>) session.getAttribute(SESSION_PUBLIC_KEY_JWK);
        return jwk;
    }

    /**
     * Sign a DPoP proof payload with the stored private key.
     * @param payloadJson JSON string containing the DPoP proof payload (jti, htm, htu, iat, ath)
     * @param session The HTTP session
     * @return Signed DPoP proof JWT
     */
    public String signDPoPProof(String payloadJson, jakarta.servlet.http.HttpSession session) {
        initializeKeyPair(session);

        try {
            KeyPair keyPair = (KeyPair) session.getAttribute(SESSION_KEY_PAIR);

            // Add typ header
            Map<String, Object> headers = new HashMap<>();
            headers.put("typ", "dpop+jwt");
            headers.put("alg", "ES256");

            return Jwts.builder()
                    .header().add(headers).and()
                    .content(payloadJson)
                    .signWith(keyPair.getPrivate(), Jwts.SIG.ES256)
                    .compact();
        } catch (Exception e) {
            log.error("Failed to sign DPoP proof", e);
            throw new RuntimeException("Failed to sign DPoP proof", e);
        }
    }

    /**
     * Get the JWK thumbprint of the stored public key (RFC 7638).
     * @param session The HTTP session
     * @return Base64url-encoded JWK thumbprint
     */
    public String getPublicKeyThumbprint(jakarta.servlet.http.HttpSession session) {
        initializeKeyPair(session);

        try {
            Map<String, Object> jwk = getPublicKeyJwk(session);
            return calculateJwkThumbprint(jwk);
        } catch (Exception e) {
            log.error("Failed to calculate JWK thumbprint", e);
            throw new RuntimeException("Failed to calculate JWK thumbprint", e);
        }
    }

    /**
     * Convert ECPublicKey to JWK format.
     */
    private Map<String, Object> convertPublicKeyToJwk(ECPublicKey publicKey) {
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", base64UrlEncode(publicKey.getW().getAffineX().toByteArray()));
        jwk.put("y", base64UrlEncode(publicKey.getW().getAffineY().toByteArray()));
        return jwk;
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate JWK thumbprint", e);
        }
    }

    /**
     * Base64url encode a byte array (without padding).
     */
    private String base64UrlEncode(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    /**
     * Clear the DPoP keypair from the session (for logout).
     */
    public void clearKeyPair(jakarta.servlet.http.HttpSession session) {
        session.removeAttribute(SESSION_KEY_PAIR);
        session.removeAttribute(SESSION_PUBLIC_KEY_JWK);
        log.debug("DPoP keypair cleared for session");
    }
}