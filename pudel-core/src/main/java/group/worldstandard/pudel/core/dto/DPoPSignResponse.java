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

/**
 * Response DTO for DPoP proof signing.
 */
public class DPoPSignResponse {
    private String signedProof;

    public DPoPSignResponse() {
    }

    public DPoPSignResponse(String signedProof) {
        this.signedProof = signedProof;
    }

    public String getSignedProof() {
        return signedProof;
    }

    public void setSignedProof(String signedProof) {
        this.signedProof = signedProof;
    }
}
