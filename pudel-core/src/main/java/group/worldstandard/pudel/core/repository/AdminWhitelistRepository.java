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
package group.worldstandard.pudel.core.repository;

import group.worldstandard.pudel.core.entity.AdminWhitelist;
import group.worldstandard.pudel.core.entity.AdminWhitelist.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for admin whitelist operations.
 */
@Repository
public interface AdminWhitelistRepository extends JpaRepository<AdminWhitelist, Long> {
    /**
     * Find admin by Discord user ID.
     */
    Optional<AdminWhitelist> findByDiscordUserId(String discordUserId);

    /**
     * Check if a Discord user is whitelisted and enabled.
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AdminWhitelist a WHERE a.discordUserId = :discordUserId AND a.enabled = true")
    boolean isWhitelisted(String discordUserId);

    /**
     * Find all enabled admins.
     */
    List<AdminWhitelist> findByEnabledTrue();

    /**
     * Find all admins by role.
     */
    List<AdminWhitelist> findByAdminRole(AdminRole adminRole);

    /**
     * Find all admins added by a specific user.
     */
    List<AdminWhitelist> findByAddedBy(String addedBy);

    /**
     * Check if any admin exists (for initial setup).
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AdminWhitelist a")
    boolean hasAnyAdmin();

    /**
     * Check if any owner exists.
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AdminWhitelist a WHERE a.adminRole = 'OWNER' AND a.enabled = true")
    boolean hasOwner();

    /**
     * Count enabled admins.
     */
    long countByEnabledTrue();

    /**
     * Delete by Discord user ID.
     */
    void deleteByDiscordUserId(String discordUserId);
}