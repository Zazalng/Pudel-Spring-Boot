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
package group.worldstandard.pudel.core.controller;

import group.worldstandard.pudel.core.dto.DPoPPublicKeyResponse;
import group.worldstandard.pudel.core.dto.DPoPSignRequest;
import group.worldstandard.pudel.core.dto.DPoPSignResponse;
import group.worldstandard.pudel.core.service.DPoPKeyManager;
import group.worldstandard.pudel.core.service.DPoPKeyManager.DPoPKeyInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BFF-style DPoP controller with database-backed key persistence.
 * <p>
 * Endpoints for frontend to:
 * 1. Get or create a DPoP key (returns keyId and public key)
 * 2. Sign DPoP proof payloads with the backend-managed private key
 * <p>
 * Key changes from previous implementation:
 * - Keys are stored in database, not HttpSession
 * - Frontend must include keyId to identify which key to use
 * - Keys persist across page refreshes and session timeouts
 * <p>
 * This enhances security by keeping the private key on the backend only,
 * protecting against XSS attacks that could steal client-side private keys.
 */
@RestController
@RequestMapping("/api/dpop")
@Tag(name = "DPoP", description = "Backend-managed DPoP key operations for enhanced security")
@CrossOrigin(origins = "*", maxAge = 3600)
@Transactional
public class DPoPController {
    private static final Logger log = LoggerFactory.getLogger(DPoPController.class);

    private final DPoPKeyManager dpopKeyManager;

    public DPoPController(DPoPKeyManager dpopKeyManager) {
        this.dpopKeyManager = dpopKeyManager;
    }

    /**
     * Get or create a DPoP key for the authenticated user.
     * <p>
     * If a keyId is provided in the request header and it exists, that key is returned.
     * Otherwise, a new keypair is generated and stored in the database.
     * <p>
     * The frontend should store the returned keyId in localStorage and include it
     * in subsequent requests to use the same key.
     *
     * @param keyId Optional key ID from frontend (for key retrieval)
     * @return DPoPKeyResponse containing keyId and public key JWK
     */
    @Operation(summary = "Get or create DPoP key", 
               description = "Get existing DPoP key by keyId or create a new one. Returns keyId and public key JWK.")
    @GetMapping("/key")
    public ResponseEntity<DPoPPublicKeyResponse> getOrCreateKey(
            @RequestHeader(value = "X-DPoP-Key-Id", required = false) String keyId) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(401).build();
            }

            DPoPKeyInfo keyInfo = dpopKeyManager.initializeKeyPair(userId, keyId);
            
            log.debug("DPoP key retrieved/created for user: {}, keyId: {}", userId, keyInfo.getKeyId());
            
            return ResponseEntity.ok(new DPoPPublicKeyResponse(
                    keyInfo.getKeyId(),
                    keyInfo.getPublicKeyJwk()
            ));
        } catch (Exception e) {
            log.error("Failed to get/create DPoP key", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the DPoP public key in JWK format by keyId.
     * <p>
     * This endpoint is provided for backward compatibility and debugging.
     * The preferred way to get keys is GET /api/dpop/key.
     *
     * @param keyId The key identifier
     * @return Public key as JWK
     */
    @Operation(summary = "Get DPoP public key by keyId", description = "Get the public key for a specific DPoP key")
    @GetMapping("/public-key")
    public ResponseEntity<DPoPPublicKeyResponse> getPublicKey(
            @RequestHeader(value = "X-DPoP-Key-Id") String keyId) {
        try {
            Map<String, Object> jwk = dpopKeyManager.getPublicKeyJwk(keyId);
            return ResponseEntity.ok(new DPoPPublicKeyResponse(keyId, jwk));
        } catch (Exception e) {
            log.error("Failed to get DPoP public key for keyId: {}", keyId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Sign a DPoP proof payload with the backend-managed private key.
     * <p>
     * Frontend creates the payload (jti, htm, htu, iat, ath) and sends it here for signing.
     * The keyId header identifies which key to use for signing.
     *
     * @param signRequest Contains the payload JSON to sign
     * @param keyId       The key identifier (from X-DPoP-Key-Id header)
     * @return Signed DPoP proof JWT
     */
    @Operation(summary = "Sign DPoP proof", description = "Sign a DPoP proof payload with the backend-managed private key")
    @PostMapping("/sign")
    public ResponseEntity<DPoPSignResponse> signProof(
            @RequestBody DPoPSignRequest signRequest,
            @RequestHeader(value = "X-DPoP-Key-Id") String keyId) {
        try {
            if (keyId == null || keyId.isEmpty()) {
                return ResponseEntity.badRequest().body(new DPoPSignResponse(null, "Missing X-DPoP-Key-Id header"));
            }

            String signedProof = dpopKeyManager.signDPoPProof(signRequest.getPayload(), keyId);
            return ResponseEntity.ok(new DPoPSignResponse(signedProof, null));
        } catch (Exception e) {
            log.error("Failed to sign DPoP proof for keyId: {}", keyId, e);
            return ResponseEntity.internalServerError()
                    .body(new DPoPSignResponse(null, "Failed to sign DPoP proof: " + e.getMessage()));
        }
    }

    /**
     * Get the JWK thumbprint of a DPoP public key.
     * Useful for debugging and frontend validation.
     *
     * @param keyId The key identifier
     * @return Base64url-encoded JWK thumbprint
     */
    @Operation(summary = "Get DPoP public key thumbprint", description = "Get the JWK thumbprint of a DPoP public key")
    @GetMapping("/thumbprint")
    public ResponseEntity<String> getThumbprint(
            @RequestHeader(value = "X-DPoP-Key-Id") String keyId) {
        try {
            String thumbprint = dpopKeyManager.getPublicKeyThumbprint(keyId);
            return ResponseEntity.ok(thumbprint);
        } catch (Exception e) {
            log.error("Failed to get DPoP thumbprint for keyId: {}", keyId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear (deactivate) a DPoP key by keyId.
     *
     * @param keyId The key identifier
     */
    @Operation(summary = "Clear DPoP key", description = "Deactivate a DPoP key by keyId")
    @DeleteMapping("/key")
    public ResponseEntity<Void> clearKey(
            @RequestHeader(value = "X-DPoP-Key-Id") String keyId) {
        try {
            dpopKeyManager.clearKeyPair(keyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to clear DPoP key: {}", keyId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear all DPoP keys for the current user (for logout).
     */
    @Operation(summary = "Clear all DPoP keys", description = "Deactivate all DPoP keys for the current user (logout)")
    @DeleteMapping("/keys")
    public ResponseEntity<Void> clearAllKeys() {
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(401).build();
            }
            dpopKeyManager.clearAllUserKeys(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to clear all DPoP keys", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the current authenticated user's ID from the security context.
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        return (String) auth.getPrincipal();
    }
}
