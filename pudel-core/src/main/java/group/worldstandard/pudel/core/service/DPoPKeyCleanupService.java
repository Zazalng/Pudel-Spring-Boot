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

import group.worldstandard.pudel.core.repository.DPoPKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Retention enforcement for the {@code dpop_keys} table.
 * <p>
 * Retention is keyed strictly on {@code expires_at}, never on {@code is_active}.
 * There is no refresh token in this BFF design, so a key that has not been
 * deactivated was not abandoned — it is simply still inside its session lifetime.
 * Deactivation ({@code is_active = false}) only signals an explicit logout or
 * admin revocation, which must keep working immediately; it is therefore not a
 * retention signal and rows deactivated early still live until they expire.
 * <p>
 * Deletion happens at {@code expires_at}, which is the same boundary
 * {@link DPoPKeyManager#findActiveSession(String)} enforces at request time. By the
 * time a row is deleted it is already unusable, so this sweep removes private key
 * material and stored JWTs without shortening any effective session.
 */
@Component
public class DPoPKeyCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DPoPKeyCleanupService.class);

    private final DPoPKeyRepository dpopKeyRepository;

    /**
     * Whether the daily sweep runs at all. Off is useful for a host that wants to
     * manage the table manually.
     */
    @Value("${pudel.dpop.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    public DPoPKeyCleanupService(DPoPKeyRepository dpopKeyRepository) {
        this.dpopKeyRepository = dpopKeyRepository;
    }

    /**
     * Daily sweep, default 04:00 server time.
     */
    @Scheduled(cron = "${pudel.dpop.cleanup.cron:0 0 4 * * ?}")
    @Transactional
    public void purgeExpiredKeys() {
        if (!cleanupEnabled) {
            return;
        }

        Instant now = Instant.now();
        try {
            int removed = dpopKeyRepository.deleteExpiredKeys(now);
            if (removed > 0) {
                log.info("DPoP key retention sweep removed {} key(s) that reached expires_at at or before {}",
                        removed, now);
            }
        } catch (Exception e) {
            // Never let a failed sweep kill the scheduler thread; the next run retries.
            log.error("DPoP key retention sweep failed at {}", now, e);
        }
    }
}