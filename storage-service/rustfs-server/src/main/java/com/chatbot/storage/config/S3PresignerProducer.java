package com.chatbot.storage.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ApplicationScoped
public class S3PresignerProducer {

  @ConfigProperty(name = "quarkus.s3.endpoint-override")
  String endpointOverride;

  @ConfigProperty(name = "quarkus.s3.aws.region")
  String region;

  @ConfigProperty(name = "quarkus.s3.aws.credentials.static-provider.access-key-id")
  String accessKey;

  @ConfigProperty(name = "quarkus.s3.aws.credentials.static-provider.secret-access-key")
  String secretKey;

  @Produces
  @ApplicationScoped
  public S3Presigner presigner() {
    return S3Presigner.builder()
        .endpointOverride(URI.create(endpointOverride))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }
}
