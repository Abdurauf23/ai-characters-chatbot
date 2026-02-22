package com.chatbot.storage.server.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileDeleted(String type, UUID fileId, String objectKey, OffsetDateTime timestamp) {

  public FileDeleted(UUID fileId, String objectKey, OffsetDateTime timestamp) {
    this("FILE_DELETED", fileId, objectKey, timestamp);
  }
}
