package com.chatbot.storage.server.service;

import com.chatbot.storage.server.config.StorageProperties;
import com.chatbot.storage.server.dto.PresignedUrlDto;
import com.chatbot.storage.server.event.FileCreated;
import com.chatbot.storage.server.event.FileDeleted;
import com.chatbot.storage.server.event.StorageEventEmitter;
import com.chatbot.storage.server.model.FileMeta;
import com.chatbot.storage.server.model.UploadResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@ApplicationScoped
public class StorageService {

  private static final Logger LOG = Logger.getLogger(StorageService.class);
  private static final String METADATA_FILENAME = "filename";
  private static final String METADATA_CREATED_AT = "created-at";

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StorageEventEmitter events;
  private final ObjectMapper objectMapper;
  private final String bucketName;
  private final Duration presignedUrlExpiration;

  @Inject
  public StorageService(
      S3Client s3Client,
      S3Presigner s3Presigner,
      StorageProperties properties,
      StorageEventEmitter events,
      ObjectMapper objectMapper) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.events = events;
    this.objectMapper = objectMapper;
    this.bucketName = properties.bucketName();
    this.presignedUrlExpiration = properties.presignedUrl().expiration();
  }

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

  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public UploadResult upload(
      String filename, String mimeType, InputStream data, long size, String tagsJson) {

    UUID fileId = UUID.randomUUID();
    String objectKey = fileId.toString();
    String effectiveMimeType =
        (mimeType != null && !mimeType.isBlank()) ? mimeType : "application/octet-stream";

    Map<String, String> metadata = new HashMap<>();
    metadata.put(METADATA_FILENAME, filename);
    metadata.put(METADATA_CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC).toString());

    PutObjectRequest.Builder requestBuilder =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType(effectiveMimeType)
            .metadata(metadata);

    Map<String, String> tags = parseTags(tagsJson);
    if (!tags.isEmpty()) {
      requestBuilder.tagging(buildTagging(tags));
    }

    s3Client.putObject(requestBuilder.build(), RequestBody.fromInputStream(data, size));
    LOG.infof(
        "File uploaded successfully: fileId=%s, filename=%s, size=%d", fileId, filename, size);

    events.fileCreated(
        new FileCreated(
            fileId,
            filename,
            objectKey,
            effectiveMimeType,
            size,
            OffsetDateTime.now(ZoneOffset.UTC)));

    return new UploadResult(fileId, objectKey);
  }

  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public FileMeta getFileMeta(UUID fileId) {
    return fetchFileMeta(fileId);
  }

  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public InputStream download(UUID fileId) {
    String objectKey = fileId.toString();
    return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(objectKey).build());
  }

  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public void delete(UUID fileId) {
    FileMeta meta = fetchFileMeta(fileId);
    s3Client.deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(meta.objectKey()).build());
    events.fileDeleted(
        new FileDeleted(fileId, meta.objectKey(), OffsetDateTime.now(ZoneOffset.UTC)));
    LOG.infof("File deleted successfully: fileId=%s", fileId);
  }

  @CircuitBreaker(
      requestVolumeThreshold = 4,
      delay = 5000,
      successThreshold = 2,
      failureRatio = 0.5)
  public PresignedUrlDto generatePresignedUrl(UUID fileId) {
    String objectKey = fileId.toString();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(presignedUrlExpiration)
            .getObjectRequest(req -> req.bucket(bucketName).key(objectKey))
            .build();
    String url = s3Presigner.presignGetObject(presignRequest).url().toString();
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(presignedUrlExpiration);
    return new PresignedUrlDto(url, expiresAt);
  }

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
                return fetchFileMeta(UUID.fromString(obj.key()));
              } catch (Exception e) {
                LOG.warnf("Failed to retrieve metadata for object: %s", obj.key());
                return null;
              }
            })
        .filter(Objects::nonNull)
        .filter(file -> mimeType == null || mimeType.equals(file.mimeType()))
        .toList();
  }

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
    return fetchFileMeta(fileId);
  }

  private FileMeta fetchFileMeta(UUID fileId) {
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

  private Tagging buildTagging(Map<String, String> tags) {
    List<Tag> tagList =
        tags.entrySet().stream()
            .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
            .toList();
    return Tagging.builder().tagSet(tagList).build();
  }

  private Map<String, String> parseTags(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
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
