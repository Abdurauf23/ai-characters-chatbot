package com.chatbot.storage.dto;

import java.time.OffsetDateTime;

/**
 * Data transfer object for presigned URL information.
 *
 * @param url presigned URL for file access
 * @param expiresAt timestamp when the URL expires
 */
public record PresignedUrlDto(String url, OffsetDateTime expiresAt) {}
