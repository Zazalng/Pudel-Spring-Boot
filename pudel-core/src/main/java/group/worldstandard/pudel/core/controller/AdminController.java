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
package group.worldstandard.pudel.core.controller;

import group.worldstandard.pudel.core.entity.AdminWhitelist;
import group.worldstandard.pudel.core.entity.AdminWhitelist.AdminRole;
import group.worldstandard.pudel.core.entity.PluginMetadata;
import group.worldstandard.pudel.core.repository.AdminWhitelistRepository;
import group.worldstandard.pudel.core.service.PluginService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for self-hosted admin operations.
 * <p>
 * Provides administrative endpoints for managing Pudel without Discord OAuth.
 * Uses Discord user ID whitelist authentication for admin access.
 * <p>
 * Authentication Flow:
 * <ol>
 *   <li>Admin requests a challenge: GET /api/admin/challenge</li>
 *   <li>Pudel generates a random challenge and signs it with private key (server identity proof)</li>
 *   <li>Admin verifies signature using Pudel's public key (confirms it's really Pudel)</li>
 *   <li>Admin submits their Discord user ID: POST /api/admin/auth</li>
 *   <li>Pudel checks the admin_whitelist table in PostgreSQL</li>
 *   <li>If whitelisted and enabled, Pudel issues an AdminJWT (separate from normal user JWT)</li>
 *   <li>Admin uses AdminJWT for admin panel operations</li>
 * </ol>
 * <p>
 * Admin JWT vs User JWT:
 * <ul>
 *   <li>AdminJWT has subject "pudel-admin-session" and includes role claim</li>
 *   <li>User JWT has subject "user-session" for normal Discord OAuth users</li>
 *   <li>Both can coexist - admin can have both tokens</li>
 * </ul>
 * <p>
 * Endpoints:
 * <ul>
 *   <li>GET /api/admin/challenge - Get server challenge (signed by Pudel)</li>
 *   <li>GET /api/admin/public-key - Get Pudel's public key for verification</li>
 *   <li>POST /api/admin/auth - Authenticate with Discord user ID</li>
 *   <li>POST /api/admin/logout - Logout admin session</li>
 *   <li>GET /api/admin/status - Get system status</li>
 *   <li>GET /api/admin/plugins - List all plugins</li>
 *   <li>POST /api/admin/plugins/upload - Upload a plugin JAR</li>
 *   <li>POST /api/admin/plugins/{name}/enable - Enable plugin</li>
 *   <li>POST /api/admin/plugins/{name}/disable - Disable plugin</li>
 *   <li>POST /api/admin/plugins/{name}/reload - Reload plugin</li>
 *   <li>DELETE /api/admin/plugins/{name} - Remove plugin</li>
 *   <li>GET /api/admin/whitelist - List admin whitelist (OWNER only)</li>
 *   <li>POST /api/admin/whitelist - Add admin to whitelist (OWNER only)</li>
 *   <li>DELETE /api/admin/whitelist/{discordUserId} - Remove admin (OWNER only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final long CHALLENGE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final long ADMIN_SESSION_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final PluginService pluginService;
    private final AdminWhitelistRepository adminWhitelistRepository;

    // Pending challenges: challengeId -> ChallengeData
    private final Map<String, ChallengeData> pendingChallenges = new ConcurrentHashMap<>();

    // Active admin sessions: sessionId -> AdminSessionData
    private final Map<String, AdminSessionData> activeSessions = new ConcurrentHashMap<>();

    @Value("${pudel.jwt.private-key-path:keys/pv.key}")
    private String privateKeyPath;

    @Value("${pudel.jwt.public-key-path:keys/pb.key}")
    private String publicKeyPath;

    @Value("${pudel.plugin.directory:./plugins}")
    private String pluginsDirectory;

    @Value("${pudel.admin.initial-owner:}")
    private String initialOwnerId;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public AdminController(PluginService pluginService, AdminWhitelistRepository adminWhitelistRepository) {
        this.pluginService = pluginService;
        this.adminWhitelistRepository = adminWhitelistRepository;
    }

    // =====================================================
    // Key Management
    // =====================================================

    /**
     * Load RSA keys lazily.
     */
    private void ensureKeysLoaded() {
        if (privateKey == null || publicKey == null) {
            try {
                privateKey = loadPrivateKey(privateKeyPath);
                publicKey = loadPublicKey(publicKeyPath);
                log.info("RSA keys loaded successfully for admin authentication");
            } catch (Exception e) {
                log.error("Failed to load RSA keys: {}", e.getMessage());
                throw new RuntimeException("Admin authentication not available: RSA keys not configured", e);
            }
        }
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Path.of(path));
        String keyContent = new String(keyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Path.of(path));
        String keyContent = new String(keyBytes)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    // =====================================================
    // Authentication Flow
    // =====================================================

    /**
     * Get Pudel's public key for admin to verify server identity.
     * GET /api/admin/public-key
     */
    @GetMapping("/public-key")
    public ResponseEntity<?> getPublicKey() {
        try {
            byte[] keyBytes = Files.readAllBytes(Path.of(publicKeyPath));
            String publicKeyPem = new String(keyBytes);

            Map<String, Object> response = new HashMap<>();
            response.put("publicKey", publicKeyPem);
            response.put("algorithm", "RSA");
            response.put("usage", "Use this key to verify challenge signatures from Pudel");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to read public key", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Public key not available"));
        }
    }

    /**
     * Generate a challenge for admin authentication.
     * Pudel signs the challenge with its private key to prove server identity.
     * GET /api/admin/challenge
     */
    @GetMapping("/challenge")
    public ResponseEntity<?> getChallenge() {
        try {
            ensureKeysLoaded();
            ensureInitialOwner();

            // Clean expired challenges
            cleanExpiredChallenges();

            // Generate challenge
            String challengeId = UUID.randomUUID().toString();
            String challengeNonce = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            long expiry = timestamp + CHALLENGE_EXPIRY_MS;

            // Sign challenge with Pudel's private key (server identity proof)
            String signature = Jwts.builder()
                    .subject("pudel-admin-challenge")
                    .claim("challengeId", challengeId)
                    .claim("nonce", challengeNonce)
                    .claim("timestamp", timestamp)
                    .expiration(new Date(expiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            // Store challenge
            pendingChallenges.put(challengeId, new ChallengeData(challengeId, challengeNonce, expiry));

            Map<String, Object> response = new HashMap<>();
            response.put("challengeId", challengeId);
            response.put("nonce", challengeNonce);
            response.put("timestamp", timestamp);
            response.put("expiry", expiry);
            response.put("signature", signature);
            response.put("message", "Verify this signature with Pudel's public key, then submit your Discord user ID");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate challenge", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate challenge: " + e.getMessage()));
        }
    }

    /**
     * Authenticate admin with Discord user ID.
     * Checks whitelist in PostgreSQL and issues AdminJWT if valid.
     * POST /api/admin/auth
     */
    @PostMapping("/auth")
    public ResponseEntity<?> authenticate(@RequestBody AdminAuthRequest request) {
        try {
            ensureKeysLoaded();
            ensureInitialOwner();

            // Validate request
            if (request.challengeId == null || request.discordUserId == null) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Missing challengeId or discordUserId"));
            }

            // Validate Discord user ID format (snowflake - 17-19 digit number)
            if (!request.discordUserId.matches("^\\d{17,19}$")) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid Discord user ID format"));
            }

            // Get pending challenge
            ChallengeData challenge = pendingChallenges.get(request.challengeId);
            if (challenge == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid or expired challenge"));
            }

            // Check expiry
            if (System.currentTimeMillis() > challenge.expiry) {
                pendingChallenges.remove(request.challengeId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Challenge expired"));
            }

            // Check whitelist in PostgreSQL
            Optional<AdminWhitelist> adminEntry = adminWhitelistRepository.findByDiscordUserId(request.discordUserId);

            if (adminEntry.isEmpty()) {
                log.warn("Admin authentication failed: Discord user {} not in whitelist", request.discordUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Discord user not authorized for admin access"));
            }

            AdminWhitelist admin = adminEntry.get();

            if (!admin.isEnabled()) {
                log.warn("Admin authentication failed: Discord user {} is disabled", request.discordUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Admin account is disabled"));
            }

            // Challenge verified - remove it (one-time use)
            pendingChallenges.remove(request.challengeId);

            // Update last login
            admin.setLastLogin(LocalDateTime.now());
            if (request.discordUsername != null) {
                admin.setDiscordUsername(request.discordUsername);
            }
            adminWhitelistRepository.save(admin);

            // Generate AdminJWT session token
            String sessionId = UUID.randomUUID().toString();
            long sessionExpiry = System.currentTimeMillis() + ADMIN_SESSION_EXPIRY_MS;

            String adminToken = Jwts.builder()
                    .subject("pudel-admin-session")  // Different from user JWT
                    .claim("sessionId", sessionId)
                    .claim("discordUserId", admin.getDiscordUserId())
                    .claim("discordUsername", admin.getDiscordUsername())
                    .claim("adminRole", admin.getAdminRole().name())
                    .claim("canModify", admin.canModify())
                    .claim("canManageAdmins", admin.canManageAdmins())
                    .issuedAt(new Date())
                    .expiration(new Date(sessionExpiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();

            // Store active session
            activeSessions.put(sessionId, new AdminSessionData(
                    sessionId,
                    admin.getDiscordUserId(),
                    admin.getAdminRole(),
                    sessionExpiry
            ));

            log.info("Admin authenticated successfully: {} ({}) with role {}",
                    admin.getDiscordUsername(), admin.getDiscordUserId(), admin.getAdminRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin authentication successful");
            response.put("adminToken", adminToken);
            response.put("discordUserId", admin.getDiscordUserId());
            response.put("discordUsername", admin.getDiscordUsername());
            response.put("adminRole", admin.getAdminRole().name());
            response.put("canModify", admin.canModify());
            response.put("canManageAdmins", admin.canManageAdmins());
            response.put("expiresAt", sessionExpiry);
            response.put("expiresIn", ADMIN_SESSION_EXPIRY_MS / 1000);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Admin authentication failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Validate admin session token from Authorization header.
     * Returns AdminSessionData if valid, null otherwise.
     */
    private AdminSessionData validateAdminSession(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);

        try {
            ensureKeysLoaded();

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Verify it's an admin session token (not user JWT)
            if (!"pudel-admin-session".equals(claims.getSubject())) {
                return null;
            }

            String sessionId = claims.get("sessionId", String.class);
            if (sessionId == null) {
                return null;
            }

            // Check if session is still active
            AdminSessionData session = activeSessions.get(sessionId);
            if (session == null || System.currentTimeMillis() > session.expiry) {
                activeSessions.remove(sessionId);
                return null;
            }

            return session;
        } catch (Exception e) {
            log.debug("Admin session validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Logout - invalidate admin session.
     * POST /api/admin/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                ensureKeysLoaded();
                String token = authHeader.substring(7);
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String sessionId = claims.get("sessionId", String.class);
                if (sessionId != null) {
                    AdminSessionData session = activeSessions.remove(sessionId);
                    if (session != null) {
                        log.info("Admin session logged out: {} ({})", session.discordUserId, sessionId);
                    }
                }
            } catch (Exception ignored) {
                // Token invalid, session already gone
            }
        }

        return ResponseEntity.ok(new SuccessResponse("Logged out successfully"));
    }

    /**
     * Ensure initial owner is created if configured and no admins exist.
     */
    private void ensureInitialOwner() {
        if (initialOwnerId != null && !initialOwnerId.isEmpty() && !adminWhitelistRepository.hasAnyAdmin()) {
            AdminWhitelist owner = new AdminWhitelist(initialOwnerId, AdminRole.OWNER);
            owner.setNote("Initial owner configured via PUDEL_ADMIN_INITIAL_OWNER");
            adminWhitelistRepository.save(owner);
            log.info("Created initial admin owner: {}", initialOwnerId);
        }
    }

    /**
     * Clean expired challenges and sessions.
     */
    private void cleanExpiredChallenges() {
        long now = System.currentTimeMillis();
        pendingChallenges.entrySet().removeIf(e -> now > e.getValue().expiry);
        activeSessions.entrySet().removeIf(e -> now > e.getValue().expiry);
    }

    // Session data classes
    private static class ChallengeData {
        final String challengeId;
        final String nonce;
        final long expiry;

        ChallengeData(String challengeId, String nonce, long expiry) {
            this.challengeId = challengeId;
            this.nonce = nonce;
            this.expiry = expiry;
        }
    }

    private static class AdminSessionData {
        final String sessionId;
        final String discordUserId;
        final AdminRole adminRole;
        final long expiry;

        AdminSessionData(String sessionId, String discordUserId, AdminRole adminRole, long expiry) {
            this.sessionId = sessionId;
            this.discordUserId = discordUserId;
            this.adminRole = adminRole;
            this.expiry = expiry;
        }

        boolean canModify() {
            return adminRole == AdminRole.OWNER || adminRole == AdminRole.ADMIN;
        }

        boolean canManageAdmins() {
            return adminRole == AdminRole.OWNER;
        }
    }

    // =====================================================
    // Admin Whitelist Management (OWNER only)
    // =====================================================

    /**
     * Get all admin whitelist entries.
     * GET /api/admin/whitelist
     */
    @GetMapping("/whitelist")
    public ResponseEntity<?> getWhitelist(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        try {
            List<AdminWhitelist> admins = adminWhitelistRepository.findAll();
            List<Map<String, Object>> adminList = admins.stream()
                    .map(this::adminToMap)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("admins", adminList);
            response.put("total", admins.size());
            response.put("enabled", admins.stream().filter(AdminWhitelist::isEnabled).count());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting admin whitelist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get admin whitelist: " + e.getMessage()));
        }
    }

    /**
     * Add a Discord user to admin whitelist.
     * POST /api/admin/whitelist
     */
    @PostMapping("/whitelist")
    public ResponseEntity<?> addAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody AddAdminRequest request) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        if (request.discordUserId == null || !request.discordUserId.matches("^\\d{17,19}$")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid Discord user ID"));
        }

        try {
            // Check if already exists
            if (adminWhitelistRepository.findByDiscordUserId(request.discordUserId).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Discord user already in whitelist"));
            }

            AdminRole role = AdminRole.ADMIN;
            if (request.adminRole != null) {
                try {
                    role = AdminRole.valueOf(request.adminRole.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Invalid admin role. Valid: OWNER, ADMIN, MODERATOR"));
                }
            }

            // Cannot create another OWNER unless you're the only one
            if (role == AdminRole.OWNER && adminWhitelistRepository.hasOwner()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Cannot create multiple OWNERs"));
            }

            AdminWhitelist newAdmin = new AdminWhitelist(request.discordUserId, request.discordUsername, role);
            newAdmin.setAddedBy(session.discordUserId);
            newAdmin.setNote(request.note);
            adminWhitelistRepository.save(newAdmin);

            log.info("Admin added to whitelist: {} ({}) by {}",
                    request.discordUserId, role, session.discordUserId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin added to whitelist");
            response.put("admin", adminToMap(newAdmin));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adding admin to whitelist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add admin: " + e.getMessage()));
        }
    }

    /**
     * Update admin whitelist entry.
     * PUT /api/admin/whitelist/{discordUserId}
     */
    @PutMapping("/whitelist/{discordUserId}")
    public ResponseEntity<?> updateAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String discordUserId,
            @RequestBody UpdateAdminRequest request) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        try {
            Optional<AdminWhitelist> adminOpt = adminWhitelistRepository.findByDiscordUserId(discordUserId);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            AdminWhitelist admin = adminOpt.get();

            // Cannot modify the last OWNER
            if (admin.getAdminRole() == AdminRole.OWNER &&
                request.adminRole != null &&
                !request.adminRole.equalsIgnoreCase("OWNER")) {
                long ownerCount = adminWhitelistRepository.findByAdminRole(AdminRole.OWNER).size();
                if (ownerCount <= 1) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Cannot demote the last OWNER"));
                }
            }

            if (request.adminRole != null) {
                try {
                    admin.setAdminRole(AdminRole.valueOf(request.adminRole.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Invalid admin role"));
                }
            }

            if (request.enabled != null) {
                admin.setEnabled(request.enabled);
            }

            if (request.note != null) {
                admin.setNote(request.note);
            }

            adminWhitelistRepository.save(admin);

            log.info("Admin updated: {} by {}", discordUserId, session.discordUserId);

            return ResponseEntity.ok(new SuccessResponse("Admin updated"));
        } catch (Exception e) {
            log.error("Error updating admin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update admin: " + e.getMessage()));
        }
    }

    /**
     * Remove a Discord user from admin whitelist.
     * DELETE /api/admin/whitelist/{discordUserId}
     */
    @DeleteMapping("/whitelist/{discordUserId}")
    public ResponseEntity<?> removeAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String discordUserId) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canManageAdmins()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only OWNER can manage admin whitelist"));
        }

        // Cannot remove yourself
        if (discordUserId.equals(session.discordUserId)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Cannot remove yourself from whitelist"));
        }

        try {
            Optional<AdminWhitelist> adminOpt = adminWhitelistRepository.findByDiscordUserId(discordUserId);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            AdminWhitelist admin = adminOpt.get();

            // Cannot remove the last OWNER
            if (admin.getAdminRole() == AdminRole.OWNER) {
                long ownerCount = adminWhitelistRepository.findByAdminRole(AdminRole.OWNER).size();
                if (ownerCount <= 1) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponse("Cannot remove the last OWNER"));
                }
            }

            adminWhitelistRepository.delete(admin);

            // Invalidate any active sessions for this user
            activeSessions.entrySet().removeIf(e -> e.getValue().discordUserId.equals(discordUserId));

            log.info("Admin removed from whitelist: {} by {}", discordUserId, session.discordUserId);

            return ResponseEntity.ok(new SuccessResponse("Admin removed from whitelist"));
        } catch (Exception e) {
            log.error("Error removing admin from whitelist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove admin: " + e.getMessage()));
        }
    }

    private Map<String, Object> adminToMap(AdminWhitelist admin) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", admin.getId());
        map.put("discordUserId", admin.getDiscordUserId());
        map.put("discordUsername", admin.getDiscordUsername());
        map.put("adminRole", admin.getAdminRole().name());
        map.put("enabled", admin.isEnabled());
        map.put("canModify", admin.canModify());
        map.put("canManageAdmins", admin.canManageAdmins());
        map.put("note", admin.getNote());
        map.put("addedBy", admin.getAddedBy());
        map.put("lastLogin", admin.getLastLogin());
        map.put("createdAt", admin.getCreatedAt());
        map.put("updatedAt", admin.getUpdatedAt());
        return map;
    }

    // =====================================================
    // System Status
    // =====================================================

    /**
     * Get comprehensive system status.
     * GET /api/admin/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            Map<String, Object> status = new HashMap<>();

            // Admin info
            Map<String, Object> adminInfo = new HashMap<>();
            adminInfo.put("discordUserId", session.discordUserId);
            adminInfo.put("adminRole", session.adminRole.name());
            adminInfo.put("canModify", session.canModify());
            adminInfo.put("canManageAdmins", session.canManageAdmins());
            status.put("admin", adminInfo);

            // System info
            Runtime runtime = Runtime.getRuntime();
            status.put("javaVersion", System.getProperty("java.version"));
            status.put("osName", System.getProperty("os.name"));
            status.put("osVersion", System.getProperty("os.version"));

            // Memory
            Map<String, Object> memory = new HashMap<>();
            memory.put("max", runtime.maxMemory() / (1024 * 1024) + " MB");
            memory.put("total", runtime.totalMemory() / (1024 * 1024) + " MB");
            memory.put("free", runtime.freeMemory() / (1024 * 1024) + " MB");
            memory.put("used", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB");
            status.put("memory", memory);

            // Plugins
            List<PluginMetadata> plugins = pluginService.getAllPlugins();
            Map<String, Object> pluginStats = new HashMap<>();
            pluginStats.put("total", plugins.size());
            pluginStats.put("enabled", plugins.stream().filter(PluginMetadata::isEnabled).count());
            pluginStats.put("loaded", plugins.stream().filter(PluginMetadata::isLoaded).count());
            status.put("plugins", pluginStats);

            // Admins
            Map<String, Object> adminStats = new HashMap<>();
            adminStats.put("total", adminWhitelistRepository.count());
            adminStats.put("enabled", adminWhitelistRepository.countByEnabledTrue());
            status.put("admins", adminStats);

            // Plugins directory
            File dir = new File(pluginsDirectory);
            status.put("pluginsDirectory", dir.getAbsolutePath());
            status.put("pluginsDirectoryExists", dir.exists());
            status.put("pluginsDirectoryWritable", dir.canWrite());

            status.put("timestamp", OffsetDateTime.now().toString());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting system status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get system status: " + e.getMessage()));
        }
    }

    // =====================================================
    // Plugin Management
    // =====================================================

    /**
     * Get all plugins with detailed information.
     * GET /api/admin/plugins
     */
    @GetMapping("/plugins")
    public ResponseEntity<?> getAllPlugins(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            List<PluginMetadata> plugins = pluginService.getAllPlugins();
            List<Map<String, Object>> pluginList = plugins.stream()
                    .map(this::pluginToMap)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("plugins", pluginList);
            response.put("total", plugins.size());
            response.put("enabled", plugins.stream().filter(PluginMetadata::isEnabled).count());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting plugins", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get plugins: " + e.getMessage()));
        }
    }

    /**
     * Upload a plugin JAR file.
     * POST /api/admin/plugins/upload
     */
    @PostMapping("/plugins/upload")
    public ResponseEntity<?> uploadPlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("file") MultipartFile file) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to upload plugins"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("No file provided"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".jar")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Only JAR files are allowed"));
        }

        try {
            // Ensure plugins directory exists
            File dir = new File(pluginsDirectory);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    log.warn("Could not create plugins directory: {}", dir.getAbsolutePath());
                }
            }

            // Save the file
            Path targetPath = dir.toPath().resolve(originalFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Plugin JAR uploaded by {}: {}", session.discordUserId, originalFilename);

            // The plugin watcher will automatically detect and load the new plugin
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Plugin uploaded successfully: " + originalFilename);
            response.put("filename", originalFilename);
            response.put("size", file.getSize());
            response.put("path", targetPath.toAbsolutePath().toString());
            response.put("uploadedBy", session.discordUserId);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error uploading plugin: {}", originalFilename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to upload plugin: " + e.getMessage()));
        }
    }

    /**
     * Enable a plugin.
     * POST /api/admin/plugins/{name}/enable
     */
    @PostMapping("/plugins/{name}/enable")
    public ResponseEntity<?> enablePlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to modify plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            pluginService.enablePlugin(name);

            log.info("Plugin enabled by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin enabled: " + name));
        } catch (Exception e) {
            log.error("Error enabling plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to enable plugin: " + e.getMessage()));
        }
    }

    /**
     * Disable a plugin.
     * POST /api/admin/plugins/{name}/disable
     */
    @PostMapping("/plugins/{name}/disable")
    public ResponseEntity<?> disablePlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to modify plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            pluginService.disablePlugin(name);

            log.info("Plugin disabled by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin disabled: " + name));
        } catch (Exception e) {
            log.error("Error disabling plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to disable plugin: " + e.getMessage()));
        }
    }

    /**
     * Reload a plugin (unload and load again).
     * POST /api/admin/plugins/{name}/reload
     */
    @PostMapping("/plugins/{name}/reload")
    public ResponseEntity<?> reloadPlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to modify plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            boolean wasEnabled = pluginService.isPluginEnabled(name);

            // Unload
            pluginService.unloadPlugin(name);

            // Re-discover (plugin watcher should handle this, but we can wait)
            Thread.sleep(500); // Give file system time

            // If was enabled, re-enable
            if (wasEnabled) {
                pluginService.enablePlugin(name);
            }

            log.info("Plugin reloaded by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin reloaded: " + name));
        } catch (Exception e) {
            log.error("Error reloading plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to reload plugin: " + e.getMessage()));
        }
    }

    /**
     * Remove a plugin (unload and delete JAR).
     * DELETE /api/admin/plugins/{name}
     */
    @DeleteMapping("/plugins/{name}")
    public ResponseEntity<?> removePlugin(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String name) {

        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        if (!session.canModify()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Insufficient permissions to remove plugins"));
        }

        try {
            Optional<PluginMetadata> plugin = pluginService.getPlugin(name);
            if (plugin.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Unload first
            pluginService.unloadPlugin(name);

            // Delete JAR file
            String jarFileName = plugin.get().getJarFileName();
            if (jarFileName != null) {
                File file = new File(pluginsDirectory, jarFileName);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        log.warn("Could not delete JAR file: {}", file.getAbsolutePath());
                    }
                }
            }

            log.info("Plugin removed by {}: {}", session.discordUserId, name);
            return ResponseEntity.ok(new SuccessResponse("Plugin removed: " + name));
        } catch (Exception e) {
            log.error("Error removing plugin: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove plugin: " + e.getMessage()));
        }
    }

    /**
     * Get list of JAR files in plugins directory (including unloaded ones).
     * GET /api/admin/plugins/files
     */
    @GetMapping("/plugins/files")
    public ResponseEntity<?> getPluginFiles(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        AdminSessionData session = validateAdminSession(authHeader);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or missing admin token"));
        }

        try {
            File dir = new File(pluginsDirectory);
            List<Map<String, Object>> files = new ArrayList<>();

            if (dir.exists() && dir.isDirectory()) {
                File[] jarFiles = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                if (jarFiles != null) {
                    for (File file : jarFiles) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("size", file.length());
                        fileInfo.put("lastModified", file.lastModified());
                        fileInfo.put("path", file.getAbsolutePath());
                        files.add(fileInfo);
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("files", files);
            response.put("total", files.size());
            response.put("directory", dir.getAbsolutePath());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting plugin files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get plugin files: " + e.getMessage()));
        }
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private Map<String, Object> pluginToMap(PluginMetadata plugin) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", plugin.getId());
        map.put("name", plugin.getPluginName());
        map.put("version", plugin.getPluginVersion());
        map.put("author", plugin.getPluginAuthor());
        map.put("description", plugin.getPluginDescription());
        map.put("jarFile", plugin.getJarFileName());
        map.put("enabled", plugin.isEnabled());
        map.put("loaded", plugin.isLoaded());
        map.put("loadError", plugin.getLoadError());
        map.put("createdAt", plugin.getCreatedAt());
        map.put("updatedAt", plugin.getUpdatedAt());
        return map;
    }

    // =====================================================
    // DTOs
    // =====================================================

    public static class AdminAuthRequest {
        public String challengeId;
        public String discordUserId;
        public String discordUsername; // Optional, for display

        public String getChallengeId() { return challengeId; }
        public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
        public String getDiscordUserId() { return discordUserId; }
        public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }
        public String getDiscordUsername() { return discordUsername; }
        public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }
    }

    public static class AddAdminRequest {
        public String discordUserId;
        public String discordUsername;
        public String adminRole; // OWNER, ADMIN, MODERATOR
        public String note;

        public String getDiscordUserId() { return discordUserId; }
        public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }
        public String getDiscordUsername() { return discordUsername; }
        public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }
        public String getAdminRole() { return adminRole; }
        public void setAdminRole(String adminRole) { this.adminRole = adminRole; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class UpdateAdminRequest {
        public String adminRole;
        public Boolean enabled;
        public String note;

        public String getAdminRole() { return adminRole; }
        public void setAdminRole(String adminRole) { this.adminRole = adminRole; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class SuccessResponse {
        public boolean success = true;
        public String message;

        public SuccessResponse(String message) {
            this.message = message;
        }
    }

    public static class ErrorResponse {
        public boolean success = false;
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
