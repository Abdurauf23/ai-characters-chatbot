package com.chatbot.storage.server.resource;

import com.chatbot.storage.client.api.FilesApi;
import com.chatbot.storage.client.model.FileResponse;
import com.chatbot.storage.client.model.FileUploadResponse;
import com.chatbot.storage.client.model.PresignedUrlResponse;
import com.chatbot.storage.server.dto.PresignedUrlDto;
import com.chatbot.storage.server.mapper.ResponseMapper;
import com.chatbot.storage.server.model.FileMeta;
import com.chatbot.storage.server.model.UploadResult;
import com.chatbot.storage.server.service.StorageService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FileResource implements FilesApi {

  @Inject StorageService storage;
  @Inject ResponseMapper responseMapper;

  public File getFile(UUID fileId) {
    FileMeta meta = storage.getFileMeta(fileId);
    InputStream stream = storage.download(fileId);
    return Response.ok(stream, meta.mimeType())
        .header("Content-Disposition", "attachment; filename=\"" + meta.filename() + "\"")
        .header("Content-Length", meta.size())
        .build();
  }

  public void deleteFile(@PathParam("fileId") UUID fileId) {
    storage.delete(fileId);
  }

  public PresignedUrlResponse getPresignedUrl(@PathParam("fileId") UUID fileId) {
    PresignedUrlDto dto = storage.generatePresignedUrl(fileId);
    return responseMapper.toPresignedUrlResponse(dto);
  }

  public List<FileResponse> searchFiles(String prefix, String mimeType, @DefaultValue("100") Integer limit) {
    return storage.search(prefix, mimeType, limit).stream().map(FileResource::toFileResponse).toList();
  }

  public FileResponse updateMetadata(
      UUID fileId, Map<String, String> requestBody) {
    return responseMapper.toFileResponse(storage.updateTags(fileId, requestBody));
  }

  public FileUploadResponse uploadFile(InputStream _fileInputStream, String filename, String contentType, String tags) {
    try (InputStream is = Files.newInputStream(form._file.toPath())) {
      UploadResult result =
              storage.upload(form.filename, form.contentType, is, form._file.length(), form.tags);
      return Response.status(Response.Status.CREATED)
              .entity(new FileUploadResponse().fileId(result.fileId()).objectKey(result.objectKey()))
              .build();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read uploaded file", e);
    }
  }
}
