package com.chatbot.storage.server.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class StorageEventEmitter {

  @Channel("storage-events-out")
  Emitter<String> emitter;

  @Inject ObjectMapper objectMapper;

  public void fileCreated(FileCreated event) {
    emitter.send(serialize(event));
  }

  public void fileDeleted(FileDeleted event) {
    emitter.send(serialize(event));
  }

  private String serialize(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize event", e);
    }
  }
}
