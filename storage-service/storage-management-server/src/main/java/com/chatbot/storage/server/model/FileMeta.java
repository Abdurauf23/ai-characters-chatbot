package com.chatbot.storage.server.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileMeta(
    UUID id,
    String filename,
    long size,
    String mimeType,
    String objectKey,
    OffsetDateTime createdAt) {}
