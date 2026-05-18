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
package group.worldstandard.pudel.core.brain;

import java.io.InputStream;

/**
 * Represents a file attachment that can be sent to Discord.
 * <p>
 * Wraps file data (as an InputStream) with metadata like filename and content type.
 * Used by PudelBrain's attachment reply methods to send images, videos, and other files.
 *
 * @param fileName    the name of the file (e.g., "image.png")
 * @param data        the file data as an InputStream
 * @param contentType the MIME type of the file (e.g., "image/png"), nullable
 */
public record FileAttachment(String fileName, InputStream data, String contentType) {

    /**
     * Create a file attachment with auto-detected content type.
     *
     * @param fileName the name of the file
     * @param data     the file data
     */
    public FileAttachment(String fileName, InputStream data) {
        this(fileName, data, detectContentType(fileName));
    }

    /**
     * Detect content type from file extension.
     */
    private static String detectContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}

