package com.chatbot.storage.server.model;

import java.util.UUID;

public record UploadResult(UUID fileId, String objectKey) {}
