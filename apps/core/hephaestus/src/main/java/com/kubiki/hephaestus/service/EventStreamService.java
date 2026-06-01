package com.kubiki.hephaestus.service;

import com.kubiki.hephaestus.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class EventStreamService {
    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registers a new SseEmitter client.
     */
    public void register(SseEmitter emitter) {
        this.emitters.add(emitter);
        
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError(e -> this.emitters.remove(emitter));
        
        log.info("Registered new SseEmitter client. Total active: {}", emitters.size());
    }

    /**
     * Publishes a telemetry event to all registered SseEmitter clients.
     */
    public void publish(TelemetryEvent event) {
        log.debug("Publishing telemetry event to stream: {}", event.type());
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(event.payload())
                        .id(String.valueOf(event.timestamp())));
            } catch (Exception e) {
                log.debug("Failed to send event to client, marking for removal: {}", e.getMessage());
                deadEmitters.add(emitter);
            }
        }
        
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.debug("Cleaned up {} disconnected clients. Total active: {}", deadEmitters.size(), emitters.size());
        }
    }
}
