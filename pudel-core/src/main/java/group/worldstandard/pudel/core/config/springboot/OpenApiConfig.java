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
package group.worldstandard.pudel.core.config.springboot;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration for Pudel REST API.
 * <p>
 * Provides interactive API documentation at /swagger-ui.html
 * and the OpenAPI 3.0 JSON spec at /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {


    @Bean
    public OpenAPI pudelOpenAPI() {
        final String dpopSchemeName = "DPoP";
        final String bearerSchemeName = "Bearer";
        final String adminBearerSchemeName = "AdminBearer";

        return new OpenAPI()
                .info(new Info()
                        .title("Pudel Discord Bot API")
                        .description("""
                                REST API for the Pudel Discord Bot management platform.

                                ## Authentication
                                - **Bearer**: Standard JWT token from Discord OAuth callback
                                - **DPoP**: Proof-of-Possession bound JWT token (RFC 9449)
                                - **AdminBearer**: Admin session JWT from mutual RSA authentication

                                ## Getting Started
                                1. Authenticate via Discord OAuth at `/api/auth/discord/callback`
                                2. Use the returned token in the `Authorization` header
                                3. For DPoP tokens, include a DPoP proof in each request
                                """)
                        .version("2.1.0")
                        .contact(new Contact()
                                .name("World Standard Group")
                                .email("kenghide@hotmail.com")
                                .url("https://worldstandard.group"))
                        .license(new License()
                                .name("AGPL-3.0 with Plugin Exception")
                                .url("https://github.com/World-Standard-Group/Pudel-Spring-Boot/blob/main/LICENSE")))
                .servers(List.of(
                        new Server().url("/").description("Current Server")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerSchemeName)
                        .addList(dpopSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                                .name(bearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token from Discord OAuth callback"))
                        .addSecuritySchemes(dpopSchemeName, new SecurityScheme()
                                .name(dpopSchemeName)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("DPoP proof token (RFC 9449). Include DPoP proof in 'DPoP' header and use 'DPoP <token>' in Authorization header."))
                        .addSecuritySchemes(adminBearerSchemeName, new SecurityScheme()
                                .name(adminBearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Admin JWT from mutual RSA authentication flow")));
    }
}

