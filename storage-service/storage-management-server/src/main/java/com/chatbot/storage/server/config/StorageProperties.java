package com.chatbot.storage.server.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.net.URI;
import java.time.Duration;

/**
 * Centralized configuration properties for S3-compatible object storage.
 *
 * <p>This configuration supports any S3-compatible storage backend including:
 *
 * <ul>
 *   <li>RustFS (local development)
 *   <li>MinIO
 *   <li>AWS S3
 *   <li>Ceph
 *   <li>Other S3-compatible providers
 * </ul>
 */
@ConfigMapping(prefix = "storage")
public interface StorageProperties {

  /** Primary bucket name for storing files. */
  @WithName("bucket-name")
  @WithDefault("storage")
  String bucketName();

  /** S3-compatible endpoint configuration. */
  S3Compatible s3();

  /** Presigned URL configuration. */
  PresignedUrl presignedUrl();

  /** Circuit breaker configuration for storage operations. */
  CircuitBreaker circuitBreaker();

  /** S3-compatible storage endpoint and credentials configuration. */
  interface S3Compatible {
    /** S3-compatible endpoint URL (e.g., http://localhost:9000 for RustFS/MinIO). */
    URI endpoint();

    /** Access key ID for authentication. */
    @WithName("access-key-id")
    String accessKeyId();

    /** Secret access key for authentication. */
    @WithName("secret-access-key")
    String secretAccessKey();

    /** Enable path-style access (required for most S3-compatible storage). */
    @WithName("path-style-access")
    @WithDefault("true")
    boolean pathStyleAccess();
  }

  /** Presigned URL generation configuration. */
  interface PresignedUrl {
    /** How long presigned URLs remain valid. */
    @WithDefault("1h")
    Duration expiration();
  }

  /** Circuit breaker configuration for fault tolerance. */
  interface CircuitBreaker {
    /** Number of consecutive failures before opening circuit. */
    @WithName("request-volume-threshold")
    @WithDefault("4")
    int requestVolumeThreshold();

    /** How long to wait before attempting to close circuit. */
    @WithName("delay")
    @WithDefault("5s")
    Duration delay();

    /** Success threshold for closing circuit. */
    @WithName("success-threshold")
    @WithDefault("2")
    int successThreshold();
  }
}
