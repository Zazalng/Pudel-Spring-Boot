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
package group.worldstandard.pudel.core.dto;

import java.util.Map;

/**
 * Response DTO for DPoP public key endpoint.
 */
public class DPoPPublicKeyResponse {
    private Map<String, Object> jwk;

    public DPoPPublicKeyResponse() {
    }

    public DPoPPublicKeyResponse(Map<String, Object> jwk) {
        this.jwk = jwk;
    }

    public Map<String, Object> getJwk() {
        return jwk;
    }

    public void setJwk(Map<String, Object> jwk) {
        this.jwk = jwk;
    }
}