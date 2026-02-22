package com.chatbot.storage.server.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.net.URI;
import java.time.Duration;

@ConfigMapping(prefix = "storage")
public interface StorageProperties {

  @WithDefault("storage")
  String bucketName();

  S3Compatible s3();

  PresignedUrl presignedUrl();

  interface S3Compatible {
    URI endpoint();

    String accessKeyId();

    String secretAccessKey();

    @WithDefault("true")
    boolean pathStyleAccess();
  }

  interface PresignedUrl {
    @WithDefault("1h")
    Duration expiration();
  }
}
