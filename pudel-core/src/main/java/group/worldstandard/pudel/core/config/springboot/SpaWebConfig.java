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
package group.worldstandard.pudel.core.config.springboot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Configuration to serve Vue SPA from Spring Boot.
 * Enable by setting pudel.spa.enabled=true in application.yml
 * and placing the Vue build output in src/main/resources/static/
 */
@Configuration
@ConditionalOnProperty(name = "pudel.spa.enabled", havingValue = "true", matchIfMissing = false)
public class SpaWebConfig implements WebMvcConfigurer {

    /**
     * Configures resource handling for serving static content and SPA fallback.
     * Registers a resource handler that serves static resources from the classpath location {@code classpath:/static/}.
     * The resource chain is enabled with a custom resolver that performs the following:
     * - Returns the requested resource if it exists and is readable.
     * - Returns null for API-related paths such as those starting with {@code api/}, {@code v3/}, or {@code swagger-ui},
     *   preventing fallback to {@code index.html} for these routes.
     * - For all other requests, returns {@code index.html} to support SPA routing.
     *
     * @param registry the resource handler registry to configure
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);

                        // If the resource exists and is readable, return it
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // For API routes, don't fallback to index.html
                        if (resourcePath.startsWith("api/") ||
                            resourcePath.startsWith("v3/") ||
                            resourcePath.startsWith("swagger-ui")) {
                            return null;
                        }

                        // Otherwise, return index.html for SPA routing
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}

