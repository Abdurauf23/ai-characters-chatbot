package com.chatbot.storage.server.event;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class StorageEventEmitter {

  @Channel("storage-events-out")
  Emitter<Object> emitter;

  public void fileCreated(FileEvent.FileCreated event) {
    emitter.send(event);
  }

  public void fileDeleted(FileEvent.FileDeleted event) {
    emitter.send(event);
  }
}
