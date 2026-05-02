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

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.dv8tion.jda.api.JDA;
import group.worldstandard.pudel.core.config.springboot.JwtUtil;
import group.worldstandard.pudel.core.dto.OAuthCallbackResponse;
import group.worldstandard.pudel.core.dto.UserDto;
import group.worldstandard.pudel.core.entity.Guild;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.entity.User;
import group.worldstandard.pudel.core.entity.UserGuild;
import group.worldstandard.pudel.core.repository.GuildRepository;
import group.worldstandard.pudel.core.repository.GuildSettingsRepository;
import group.worldstandard.pudel.core.repository.UserRepository;
import group.worldstandard.pudel.core.repository.UserGuildRepository;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuthService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final DiscordAPIService discordAPIService;
    private final UserRepository userRepository;
    private final GuildRepository guildRepository;
    private final GuildSettingsRepository guildSettingsRepository;
    private final UserGuildRepository userGuildRepository;
    private final JwtUtil jwtUtil;
    private final DPoPService dpopService;

    public AuthService(@Lazy JDA jda,
                       DiscordAPIService discordAPIService,
                       UserRepository userRepository,
                       GuildRepository guildRepository,
                       GuildSettingsRepository guildSettingsRepository,
                       UserGuildRepository userGuildRepository,
                       JwtUtil jwtUtil,
                       DPoPService dpopService) {
        super(jda);
        this.discordAPIService = discordAPIService;
        this.userRepository = userRepository;
        this.guildRepository = guildRepository;
        this.guildSettingsRepository = guildSettingsRepository;
        this.userGuildRepository = userGuildRepository;
        this.jwtUtil = jwtUtil;
        this.dpopService = dpopService;
    }

    @Transactional
    public OAuthCallbackResponse handleOAuthCallback(String code) {
        return handleOAuthCallback(code, null, null, null, null);
    }

    @Transactional
    public OAuthCallbackResponse handleOAuthCallback(String code, String dpopProof, String httpMethod, String httpUri, HttpSession session) {
        try {
            String dpopThumbprint = null;

            if (dpopProof != null && !dpopProof.isBlank()) {
                DPoPService.DPoPValidationResult proofResult = dpopService.validateProofForResource(dpopProof, httpMethod, httpUri, null, session);

                if (!proofResult.valid()) {
                    log.warn("DPoP proof validation failed during OAuth: {}", proofResult.error());
                    return null;
                }

                dpopThumbprint = proofResult.thumbprint();
                log.info("DPoP proof validated for OAuth callback, thumbprint: {}", dpopThumbprint);
            }

            String accessToken = discordAPIService.getAccessToken(code);
            if (accessToken == null) {
                log.error("Failed to get access token");
                return null;
            }

            UserDto userDto = discordAPIService.getUserInfo(accessToken);
            if (userDto == null) {
                log.error("Failed to get user info");
                return null;
            }

            User user = userRepository.findById(userDto.getId())
                    .orElse(new User(userDto.getId(), userDto.getUsername()));

            user.setUsername(userDto.getUsername());
            user.setDiscriminator(userDto.getDiscriminator());
            user.setAvatar(userDto.getAvatar());
            user.setEmail(userDto.getEmail());
            user.setVerified(userDto.getVerified());
            user.setAccessToken(accessToken);
            user.setTokenExpiresAt(LocalDateTime.now().plusHours(24));

            userRepository.save(user);
            log.info("User saved/updated: {}", user.getId());

            List<Map<String, Object>> discordGuilds = discordAPIService.getUserGuilds(accessToken);
            syncUserGuilds(user.getId(), discordGuilds);

            Map<String, Object> claims = new HashMap<>();
            claims.put("username", user.getUsername());

            String jwtToken;
            String tokenType;
            if (dpopThumbprint != null) {
                jwtToken = jwtUtil.generateDPoPBoundToken(user.getId(), claims, dpopThumbprint);
                tokenType = JwtUtil.TOKEN_TYPE_DPOP;
                log.info("Generated DPoP-bound token for user: {}", user.getId());
            } else {
                jwtToken = jwtUtil.generateToken(user.getId(), claims);
                tokenType = JwtUtil.TOKEN_TYPE_BEARER;
                log.info("Generated Bearer token for user: {}", user.getId());
            }

            OAuthCallbackResponse response = new OAuthCallbackResponse(jwtToken, userDto);
            response.setTokenType(tokenType);
            return response;
        } catch (Exception e) {
            log.error("Error handling OAuth callback", e);
            return null;
        }
    }

    public void revokeToken(String token) {
        log.info("Token revocation requested (BFF-style - handled by session management)");
    }

    @Transactional
    protected void syncUserGuilds(String userId, List<Map<String, Object>> discordGuilds) {
        try {
            List<UserGuild> existingUserGuilds = userGuildRepository.findByUserId(userId);
            Set<String> discordGuildIds = new HashSet<>();

            for (Map<String, Object> guildData : discordGuilds) {
                discordGuildIds.add((String) guildData.get("id"));
            }

            for (UserGuild existingUserGuild : existingUserGuilds) {
                if (!discordGuildIds.contains(existingUserGuild.getGuildId())) {
                    userGuildRepository.delete(existingUserGuild);
                }
            }

            Set<String> existingGuildIds = new HashSet<>();
            for (UserGuild ug : existingUserGuilds) {
                existingGuildIds.add(ug.getGuildId());
            }

            for (Map<String, Object> guildData : discordGuilds) {
                String guildId = (String) guildData.get("id");
                Boolean owner = (Boolean) guildData.get("owner");
                Long permissions = (Long) guildData.get("permissions");

                if (!guildRepository.existsById(guildId)) {
                    Guild guild = new Guild(guildId, (String) guildData.get("name"));
                    if (guildData.get("icon") != null) {
                        guild.setIcon((String) guildData.get("icon"));
                    }
                    guildRepository.save(guild);
                }

                if (!existingGuildIds.contains(guildId)) {
                    UserGuild userGuild = new UserGuild(userId, guildId, owner, permissions);
                    userGuildRepository.save(userGuild);
                } else {
                    Optional<UserGuild> existingOpt = userGuildRepository.findByUserIdAndGuildId(userId, guildId);
                    if (existingOpt.isPresent()) {
                        UserGuild existing = existingOpt.get();
                        existing.setOwner(owner);
                        existing.setPermissions(permissions);
                        userGuildRepository.save(existing);
                    }
                }
            }

            log.info("Synchronized {} guilds for user {}", discordGuilds.size(), userId);
        } catch (Exception e) {
            log.error("Error synchronizing user guilds", e);
            throw new RuntimeException("Failed to synchronize user guilds", e);
        }
    }

    public Map<String, Object> getUserGuilds(String userId) {
        try {
            long ADMINISTRATOR_PERMISSION = 0x8L;
            List<UserGuild> userGuilds = userGuildRepository.findByUserId(userId);
            List<Map<String, Object>> managedGuilds = new ArrayList<>();
            List<Map<String, Object>> availableGuilds = new ArrayList<>();
            int filteredCount = 0;

            for (UserGuild ug : userGuilds) {
                Long permissions = ug.getPermissions();
                boolean hasAdminPermission = ug.getOwner() || (permissions != null && (permissions & ADMINISTRATOR_PERMISSION) != 0);

                if (!hasAdminPermission) {
                    continue;
                }

                filteredCount++;
                Optional<Guild> guildOpt = guildRepository.findById(ug.getGuildId());
                if (guildOpt.isPresent()) {
                    Guild guild = guildOpt.get();
                    boolean hasBot = isBotInGuild(guild.getId());

                    Map<String, Object> guildInfo = new HashMap<>();
                    guildInfo.put("id", guild.getId());
                    guildInfo.put("name", guild.getName());
                    guildInfo.put("icon", guild.getIcon());
                    guildInfo.put("owner", ug.getOwner());
                    guildInfo.put("permissions", ug.getPermissions());
                    guildInfo.put("hasBot", hasBot);
                    guildInfo.put("memberCount", guild.getMemberCount());

                    if (hasBot) {
                        managedGuilds.add(guildInfo);
                    } else {
                        availableGuilds.add(guildInfo);
                    }
                }
            }

            log.info("User {} has {} managed guilds and {} available guilds (filtered from {} total)",
                    userId,
                    managedGuilds.size(),
                    availableGuilds.size(),
                    filteredCount
            );

            List<Map<String, Object>> allGuilds = new ArrayList<>();
            allGuilds.addAll(managedGuilds);
            allGuilds.addAll(availableGuilds);

            return Map.of(
                    "guilds", allGuilds,
                    "managed", managedGuilds,
                    "available", availableGuilds,
                    "managedCount", managedGuilds.size(),
                    "availableCount", availableGuilds.size(),
                    "total", managedGuilds.size() + availableGuilds.size()
            );
        } catch (Exception e) {
            log.error("Error fetching user guilds", e);
            return Map.of(
                    "guilds", new ArrayList<>(),
                    "managed", new ArrayList<>(),
                    "available", new ArrayList<>(),
                    "managedCount", 0,
                    "availableCount", 0,
                    "total", 0
            );
        }
    }

    public Map<String, Object> getGuildInfo(String userId, String guildId) {
        try {
            if (!userGuildRepository.existsByUserIdAndGuildId(userId, guildId)) {
                log.warn("User {} does not have access to guild {}", userId, guildId);
                return null;
            }

            Optional<Guild> guildOpt = guildRepository.findById(guildId);
            Optional<UserGuild> userGuildOpt = userGuildRepository.findByUserIdAndGuildId(userId, guildId);
            if (guildOpt.isPresent() && userGuildOpt.isPresent()) {
                Guild guild = guildOpt.get();
                UserGuild userGuild = userGuildOpt.get();
                boolean hasBot = isBotInGuild(guild.getId());

                Map<String, Object> guildInfo = new HashMap<>();
                guildInfo.put("id", guild.getId());
                guildInfo.put("name", guild.getName());
                guildInfo.put("icon", guild.getIcon());
                guildInfo.put("owner", userGuild.getOwner());
                guildInfo.put("permissions", userGuild.getPermissions());
                guildInfo.put("hasBot", hasBot);
                guildInfo.put("memberCount", guild.getMemberCount());
                guildInfo.put("ownerId", guild.getOwnerId());

                Optional<GuildSettings> settingsOpt = guildSettingsRepository.findByGuildId(guildId);
                settingsOpt.ifPresent(guildSettings -> guildInfo.put("settings", guildSettings));
                return guildInfo;
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching guild info for user {} and guild {}", userId, guildId, e);
            return null;
        }
    }

    public void createDefaultGuildSettings(String guildId) {
        try {
            if (guildSettingsRepository.findByGuildId(guildId).isEmpty()) {
                GuildSettings settings = new GuildSettings(guildId);
                guildSettingsRepository.save(settings);
                log.info("Created default settings for guild: {}", guildId);
            }
        } catch (Exception e) {
            log.error("Error creating default guild settings", e);
        }
    }
}
