package com.chatbot.storage.resource;

import com.chatbot.storage.dto.FileDto;
import com.chatbot.storage.dto.PresignedUrlDto;
import com.chatbot.storage.event.FileEvent;
import com.chatbot.storage.event.StorageEventEmitter;
import com.chatbot.storage.service.StorageService;
import com.chatbot.storage.service.StorageService.FileMeta;
import com.chatbot.storage.service.StorageService.UploadResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/v1/files")
@Produces(MediaType.APPLICATION_JSON)
public class FileResource {

  @Inject StorageService storage;

  @Inject StorageEventEmitter events;

  @Inject ObjectMapper objectMapper;

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response upload(@RestForm("file") FileUpload file, @RestForm("tags") String tagsJson)
      throws Exception {
    Map<String, String> tags = parseTags(tagsJson);
    String filename = file.fileName();
    String mimeType = file.contentType();
    long size = file.size();

    try (InputStream is = Files.newInputStream(file.uploadedFile())) {
      UploadResult result = storage.upload(filename, mimeType, is, size, tags);

      events.fileCreated(
          new FileEvent.FileCreated(
              result.fileId(),
              filename,
              result.objectKey(),
              mimeType,
              size,
              OffsetDateTime.now(ZoneOffset.UTC)));

      return Response.status(Response.Status.CREATED)
          .entity(Map.of("fileId", result.fileId(), "objectKey", result.objectKey()))
          .build();
    }
  }

  @GET
  @Path("/{fileId}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getFile(@PathParam("fileId") UUID fileId) {
    FileMeta meta = storage.getFileMeta(fileId);
    InputStream stream = storage.download(fileId);
    return Response.ok(stream, meta.mimeType())
        .header("Content-Disposition", "attachment; filename=\"" + meta.filename() + "\"")
        .header("Content-Length", meta.size())
        .build();
  }

  @DELETE
  @Path("/{fileId}")
  public Response deleteFile(@PathParam("fileId") UUID fileId) {
    FileMeta meta = storage.getFileMeta(fileId);
    storage.delete(fileId);

    events.fileDeleted(
        new FileEvent.FileDeleted(fileId, meta.objectKey(), OffsetDateTime.now(ZoneOffset.UTC)));

    return Response.noContent().build();
  }

  @GET
  @Path("/{fileId}/presigned-url")
  public PresignedUrlDto getPresignedUrl(@PathParam("fileId") UUID fileId) {
    String url = storage.generatePresignedUrl(fileId);
    return new PresignedUrlDto(url, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
  }

  @GET
  @Path("/search")
  public List<FileDto> searchFiles(
      @QueryParam("prefix") String prefix,
      @QueryParam("mimeType") String mimeType,
      @QueryParam("limit") @DefaultValue("100") int limit) {
    return storage.search(prefix, mimeType, limit).stream().map(FileDto::from).toList();
  }

  @PATCH
  @Path("/{fileId}/metadata")
  @Consumes(MediaType.APPLICATION_JSON)
  public FileDto updateMetadata(@PathParam("fileId") UUID fileId, Map<String, String> tags) {
    return FileDto.from(storage.updateTags(fileId, tags));
  }

  // --- Helpers ---

  private Map<String, String> parseTags(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }
}
