package com.chatbot.storage.server.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configuration for S3-compatible object storage.
 *
 * <p>Produces S3Presigner bean configured for S3-compatible storage backends (RustFS, MinIO, AWS
 * S3, etc.). The S3Client bean is automatically provided by Quarkus Amazon Services extension.
 */
@ApplicationScoped
public class ObjectStorageConfiguration {

  private final StorageProperties properties;

  @Inject
  public ObjectStorageConfiguration(StorageProperties properties) {
    this.properties = properties;
  }

  /**
   * Produces S3Presigner configured for S3-compatible storage.
   *
   * <p>The S3Client bean is automatically provided by Quarkus Amazon Services extension based on
   * quarkus.s3.* configuration properties.
   *
   * @return configured S3Presigner instance
   */
  @Produces
  @ApplicationScoped
  public S3Presigner s3Presigner() {
    StorageProperties.S3Compatible s3Config = properties.s3();

    return S3Presigner.builder()
        .endpointOverride(s3Config.endpoint())
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3Config.accessKeyId(), s3Config.secretAccessKey())))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(s3Config.pathStyleAccess()).build())
        .build();
  }
}
