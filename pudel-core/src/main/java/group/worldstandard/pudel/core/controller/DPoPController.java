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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.util.Map;

/**
 * BFF-style DPoP controller.
 * <p>
 * Endpoints for frontend to:
 * 1. Get the backend-managed public key for DPoP
 * 2. Sign DPoP proof payloads with the backend-managed private key
 * <p>
 * This enhances security by keeping the private key on the backend only,
 * protecting against XSS attacks that could steal client-side private keys.
 */
@RestController
@RequestMapping("/api/dpop")
@Tag(name = "DPoP", description = "Backend-managed DPoP key operations for enhanced security")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DPoPController {

    private static final Logger log = LoggerFactory.getLogger(DPoPController.class);

    private final DPoPKeyManager dpopKeyManager;

    public DPoPController(DPoPKeyManager dpopKeyManager) {
        this.dpopKeyManager = dpopKeyManager;
    }

    /**
     * Get the backend-managed DPoP public key in JWK format.
     * Frontend uses this to create DPoP proof payloads.
     *
     * @param session The HTTP session (injected by Spring)
     * @return Public key as JWK
     */
    @Operation(summary = "Get DPoP public key", description = "Get the backend-managed public key for DPoP proof creation")
    @GetMapping("/public-key")
    public ResponseEntity<DPoPPublicKeyResponse> getPublicKey(HttpSession session) {
        try {
            Map<String, Object> jwk = dpopKeyManager.getPublicKeyJwk(session);
            return ResponseEntity.ok(new DPoPPublicKeyResponse(jwk));
        } catch (Exception e) {
            log.error("Failed to get DPoP public key", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Sign a DPoP proof payload with the backend-managed private key.
     * Frontend creates the payload (jti, htm, htu, iat, ath) and sends it here for signing.
     *
     * @param signRequest Contains the payload JSON to sign
     * @param session The HTTP session (injected by Spring)
     * @return Signed DPoP proof JWT
     */
    @Operation(summary = "Sign DPoP proof", description = "Sign a DPoP proof payload with the backend-managed private key")
    @PostMapping("/sign")
    public ResponseEntity<DPoPSignResponse> signProof(@RequestBody DPoPSignRequest signRequest, HttpSession session) {
        try {
            String signedProof = dpopKeyManager.signDPoPProof(signRequest.getPayload(), session);
            return ResponseEntity.ok(new DPoPSignResponse(signedProof));
        } catch (Exception e) {
            log.error("Failed to sign DPoP proof", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the JWK thumbprint of the backend-managed public key.
     * Useful for debugging and frontend validation.
     *
     * @param session The HTTP session (injected by Spring)
     * @return Base64url-encoded JWK thumbprint
     */
    @Operation(summary = "Get DPoP public key thumbprint", description = "Get the JWK thumbprint of the backend-managed public key")
    @GetMapping("/thumbprint")
    public ResponseEntity<String> getThumbprint(HttpSession session) {
        try {
            String thumbprint = dpopKeyManager.getPublicKeyThumbprint(session);
            return ResponseEntity.ok(thumbprint);
        } catch (Exception e) {
            log.error("Failed to get DPoP thumbprint", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear the DPoP keypair from the session (for logout).
     *
     * @param session The HTTP session (injected by Spring)
     */
    @Operation(summary = "Clear DPoP keypair", description = "Clear the DPoP keypair from the session (logout)")
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearKeyPair(HttpSession session) {
        dpopKeyManager.clearKeyPair(session);
        return ResponseEntity.ok().build();
    }
}
