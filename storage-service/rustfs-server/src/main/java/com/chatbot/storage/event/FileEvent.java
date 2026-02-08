package com.chatbot.storage.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class FileEvent {

  private FileEvent() {}

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

  public record FileDeleted(String type, UUID fileId, String objectKey, OffsetDateTime timestamp) {
    public FileDeleted(UUID fileId, String objectKey, OffsetDateTime timestamp) {
      this("FILE_DELETED", fileId, objectKey, timestamp);
    }
  }

  public record FileVirusScanned(
      String type, UUID fileId, boolean clean, OffsetDateTime timestamp) {
    public FileVirusScanned(UUID fileId, boolean clean, OffsetDateTime timestamp) {
      this("FILE_VIRUS_SCANNED", fileId, clean, timestamp);
    }
  }
}
