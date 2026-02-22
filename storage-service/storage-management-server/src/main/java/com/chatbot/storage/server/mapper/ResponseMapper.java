package com.chatbot.storage.server.mapper;

import com.chatbot.storage.client.model.FileResponse;
import com.chatbot.storage.client.model.PresignedUrlResponse;
import com.chatbot.storage.server.dto.PresignedUrlDto;
import com.chatbot.storage.server.model.FileMeta;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ResponseMapper {
    public FileResponse toFileResponse(FileMeta meta) {
        return new FileResponse()
                .id(meta.id())
                .filename(meta.filename())
                .size(meta.size())
                .mimeType(meta.mimeType())
                .objectKey(meta.objectKey())
                .createdAt(meta.createdAt());
    }

    public PresignedUrlResponse toPresignedUrlResponse(PresignedUrlDto presignedUrlDto) {
        return new PresignedUrlResponse()
                .url(presignedUrlDto.url())
                .expiresAt(presignedUrlDto.expiresAt());
    }
}
