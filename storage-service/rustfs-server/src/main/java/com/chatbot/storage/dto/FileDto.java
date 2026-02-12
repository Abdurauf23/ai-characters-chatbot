package com.chatbot.storage.dto;

import com.chatbot.storage.service.StorageService.FileMeta;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data transfer object for file information.
 *
 * @param id unique file identifier
 * @param filename original filename
 * @param size file size in bytes
 * @param mimeType MIME type
 * @param objectKey storage object key
 * @param createdAt file creation timestamp
 */
public record FileDto(
    UUID id,
    String filename,
    long size,
    String mimeType,
    String objectKey,
    OffsetDateTime createdAt) {

  /**
   * Converts FileMeta to FileDto.
   *
   * @param meta file metadata
   * @return file DTO
   */
  public static FileDto from(FileMeta meta) {
    return new FileDto(
        meta.id(),
        meta.filename(),
        meta.size(),
        meta.mimeType(),
        meta.objectKey(),
        meta.createdAt());
  }
}
