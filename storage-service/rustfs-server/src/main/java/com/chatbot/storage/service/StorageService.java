package com.chatbot.storage.service;

import com.chatbot.storage.config.StorageProperties;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Service layer for file storage operations.
 *
 * <p>Provides business logic for file management including upload, download, metadata handling, and
 * search. All storage operations are protected by circuit breakers for fault tolerance.
 *
 * <p>This service uses AWS S3 SDK which is compatible with S3-compatible storage backends including
 * RustFS, MinIO, Ceph, and AWS S3.
 */
@ApplicationScoped
public class StorageService {

  private static final Logger LOG = Logger.getLogger(StorageService.class);
  private static final String METADATA_FILENAME = "filename";
  private static final String METADATA_CREATED_AT = "created-at";

  /**
   * File metadata record.
   *
   * @param id unique file identifier (same as object key)
   * @param filename original filename
   * @param size file size in bytes
   * @param mimeType MIME type
   * @param objectKey storage object key
   * @param createdAt file creation timestamp
   */
  public record FileMeta(
      UUID id,
      String filename,
      long size,
      String mimeType,
      String objectKey,
      OffsetDateTime createdAt) {}

  /**
   * Result of a successful file upload.
   *
   * @param fileId unique file identifier
   * @param objectKey storage object key (currently same as fileId)
   */
  public record UploadResult(UUID fileId, String objectKey) {}

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StorageProperties properties;
  private final String bucketName;

  @Inject
  public StorageService(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.properties = properties;
    this.bucketName = properties.bucketName();
  }

  /** Ensures the storage bucket exists on application startup. */
  void onStart(@Observes StartupEvent event) {
    LOG.infof("Initializing storage service with bucket: %s", bucketName);
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      LOG.infof("Bucket exists: %s", bucketName);
    } catch (NoSuchBucketException e) {
      LOG.infof("Bucket does not exist, creating: %s", bucketName);
      s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
      LOG.infof("Successfully created bucket: %s", bucketName);
    }
  }

  /**
   * Uploads a file to storage with optional tags.
   *
   * @param filename original filename
   * @param mimeType MIME type
   * @param data input stream containing file data
   * @param size file size in bytes
   * @param tags optional metadata tags
   * @return upload result with generated file ID and object key
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public UploadResult upload(
      String filename, String mimeType, InputStream data, long size, Map<String, String> tags) {

    UUID fileId = UUID.randomUUID();
    String objectKey = fileId.toString();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(METADATA_FILENAME, filename);
    metadata.put(METADATA_CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC).toString());

    PutObjectRequest.Builder requestBuilder =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType(mimeType)
            .metadata(metadata);

    if (tags != null && !tags.isEmpty()) {
      requestBuilder.tagging(buildTagging(tags));
    }

    s3Client.putObject(requestBuilder.build(), RequestBody.fromInputStream(data, size));

    LOG.infof(
        "File uploaded successfully: fileId=%s, filename=%s, size=%d", fileId, filename, size);

    return new UploadResult(fileId, objectKey);
  }

  /**
   * Retrieves file metadata without downloading the file content.
   *
   * @param fileId unique file identifier
   * @return file metadata
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public FileMeta getFileMeta(UUID fileId) {
    String objectKey = fileId.toString();
    HeadObjectResponse response =
        s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build());

    Map<String, String> metadata = response.metadata();
    return new FileMeta(
        fileId,
        metadata.getOrDefault(METADATA_FILENAME, objectKey),
        response.contentLength(),
        response.contentType(),
        objectKey,
        parseTimestamp(metadata.get(METADATA_CREATED_AT)));
  }

  /**
   * Downloads a file as an input stream.
   *
   * <p>The caller is responsible for closing the returned stream.
   *
   * @param fileId unique file identifier
   * @return input stream containing file data
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public InputStream download(UUID fileId) {
    String objectKey = fileId.toString();
    LOG.debugf("Downloading file: fileId=%s", fileId);
    return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build());
  }

  /**
   * Deletes a file from storage.
   *
   * @param fileId unique file identifier
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public void delete(UUID fileId) {
    String objectKey = fileId.toString();
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectKey).build());
    LOG.infof("File deleted successfully: fileId=%s", fileId);
  }

  /**
   * Generates a presigned URL for temporary file access.
   *
   * @param fileId unique file identifier
   * @return presigned URL valid for the configured duration
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public String generatePresignedUrl(UUID fileId) {
    String objectKey = fileId.toString();
    Duration expiration = properties.presignedUrl().expiration();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(expiration)
            .getObjectRequest(req -> req.bucket(bucketName).key(objectKey))
            .build();

    String url = s3Presigner.presignGetObject(presignRequest).url().toString();
    LOG.debugf("Generated presigned URL for fileId=%s", fileId);
    return url;
  }

  /**
   * Searches for files based on optional filters.
   *
   * @param prefix optional object key prefix filter
   * @param mimeType optional MIME type filter
   * @param limit maximum number of results
   * @return list of matching file metadata
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public List<FileMeta> search(String prefix, String mimeType, int limit) {
    ListObjectsV2Request.Builder requestBuilder =
        ListObjectsV2Request.builder().bucket(bucketName).maxKeys(limit);

    if (prefix != null && !prefix.isBlank()) {
      requestBuilder.prefix(prefix);
    }

    return s3Client.listObjectsV2(requestBuilder.build()).contents().stream()
        .map(
            obj -> {
              try {
                return getFileMeta(UUID.fromString(obj.key()));
              } catch (Exception e) {
                LOG.warnf("Failed to retrieve metadata for object: %s", obj.key());
                return null;
              }
            })
        .filter(Objects::nonNull)
        .filter(file -> mimeType == null || mimeType.equals(file.mimeType()))
        .collect(Collectors.toList());
  }

  /**
   * Updates tags on an existing file.
   *
   * @param fileId unique file identifier
   * @param tags new tags to apply
   * @return updated file metadata
   */
  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public FileMeta updateTags(UUID fileId, Map<String, String> tags) {
    String objectKey = fileId.toString();
    s3Client.putObjectTagging(
        PutObjectTaggingRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .tagging(buildTagging(tags))
            .build());
    LOG.debugf("Updated tags for fileId=%s", fileId);
    return getFileMeta(fileId);
  }

  // --- Private helpers ---

  private Tagging buildTagging(Map<String, String> tags) {
    List<Tag> tagList =
        tags.entrySet().stream()
            .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
            .collect(Collectors.toList());
    return Tagging.builder().tagSet(tagList).build();
  }

  private static OffsetDateTime parseTimestamp(String value) {
    if (value == null) {
      return OffsetDateTime.now(ZoneOffset.UTC);
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (Exception e) {
      LOG.warnf("Failed to parse timestamp: %s", value);
      return OffsetDateTime.now(ZoneOffset.UTC);
    }
  }
}
