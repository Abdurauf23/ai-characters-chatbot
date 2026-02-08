package com.chatbot.storage.service;

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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
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

  public record FileMeta(
      UUID id,
      String filename,
      long size,
      String mimeType,
      String objectKey,
      OffsetDateTime createdAt) {}

  public record UploadResult(UUID fileId, String objectKey) {}

  @Inject S3Client s3;

  @Inject S3Presigner presigner;

  @ConfigProperty(name = "storage.bucket-name", defaultValue = "storage")
  String bucketName;

  void onStart(@Observes StartupEvent ev) {
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
    } catch (NoSuchBucketException e) {
      s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public UploadResult upload(
      String filename, String mimeType, InputStream data, long size, Map<String, String> tags) {
    UUID fileId = UUID.randomUUID();
    String objectKey = fileId.toString();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("filename", filename);
    metadata.put("created-at", OffsetDateTime.now(ZoneOffset.UTC).toString());

    PutObjectRequest.Builder req =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType(mimeType)
            .metadata(metadata);

    if (tags != null && !tags.isEmpty()) {
      req.tagging(
          Tagging.builder()
              .tagSet(
                  tags.entrySet().stream()
                      .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                      .toList())
              .build());
    }

    s3.putObject(req.build(), RequestBody.fromInputStream(data, size));
    return new UploadResult(fileId, objectKey);
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public FileMeta getFileMeta(UUID fileId) {
    HeadObjectResponse head =
        s3.headObject(
            HeadObjectRequest.builder().bucket(bucketName).key(fileId.toString()).build());

    Map<String, String> meta = head.metadata();
    return new FileMeta(
        fileId,
        meta.getOrDefault("filename", fileId.toString()),
        head.contentLength(),
        head.contentType(),
        fileId.toString(),
        parseTimestamp(meta.get("created-at")));
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public InputStream download(UUID fileId) {
    return s3.getObject(
        GetObjectRequest.builder().bucket(bucketName).key(fileId.toString()).build());
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public void delete(UUID fileId) {
    s3.deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(fileId.toString()).build());
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public String generatePresignedUrl(UUID fileId) {
    GetObjectPresignRequest req =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1))
            .getObjectRequest(b -> b.bucket(bucketName).key(fileId.toString()))
            .build();
    return presigner.presignGetObject(req).url().toString();
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public List<FileMeta> search(String prefix, String mimeType, int limit) {
    ListObjectsV2Request.Builder req =
        ListObjectsV2Request.builder().bucket(bucketName).maxKeys(limit);
    if (prefix != null) {
      req.prefix(prefix);
    }

    return s3.listObjectsV2(req.build()).contents().stream()
        .map(
            obj -> {
              try {
                return getFileMeta(UUID.fromString(obj.key()));
              } catch (Exception e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .filter(f -> mimeType == null || mimeType.equals(f.mimeType()))
        .collect(Collectors.toList());
  }

  @CircuitBreaker(requestVolumeThreshold = 4)
  public FileMeta updateTags(UUID fileId, Map<String, String> tags) {
    s3.putObjectTagging(
        PutObjectTaggingRequest.builder()
            .bucket(bucketName)
            .key(fileId.toString())
            .tagging(
                Tagging.builder()
                    .tagSet(
                        tags.entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .toList())
                    .build())
            .build());
    return getFileMeta(fileId);
  }

  private static OffsetDateTime parseTimestamp(String value) {
    if (value == null) return OffsetDateTime.now(ZoneOffset.UTC);
    try {
      return OffsetDateTime.parse(value);
    } catch (Exception e) {
      return OffsetDateTime.now(ZoneOffset.UTC);
    }
  }
}
