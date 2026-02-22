package com.chatbot.storage.server.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileCreated(
    String type,
    UUID fileId,
    String filename,
    String objectKey,
    String mimeType,
    long size,
    OffsetDateTime timestamp) {

  public FileCreated(
      UUID fileId,
      String filename,
      String objectKey,
      String mimeType,
      long size,
      OffsetDateTime timestamp) {
    this("FILE_CREATED", fileId, filename, objectKey, mimeType, size, timestamp);
  }
}
