package com.chatbot.storage.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Provider
public class StorageExceptionMapper implements ExceptionMapper<S3Exception> {

  @Override
  public Response toResponse(S3Exception e) {
    int status = (e instanceof NoSuchKeyException) ? 404 : 500;
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .entity(
            Map.of(
                "message", e.getMessage(),
                "status", status,
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()))
        .build();
  }
}
