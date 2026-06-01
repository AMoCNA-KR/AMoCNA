package com.kubiki.hephaestus.service;

import com.kubiki.hephaestus.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventStreamServiceTest {

    private EventStreamService eventStreamService;

    @BeforeEach
    void setUp() {
        eventStreamService = new EventStreamService();
    }

    @Test
    void testRegisterAndPublishSuccess() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        eventStreamService.register(emitter);

        TelemetryEvent event = new TelemetryEvent("action", "test-payload");
        eventStreamService.publish(event);

        // Verify that the emitter attempted to send the event
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void testCleanUpDeadEmittersOnException() throws IOException {
        SseEmitter deadEmitter = mock(SseEmitter.class);
        // Throw exception when trying to send
        doThrow(new IOException("Connection reset")).when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));

        eventStreamService.register(deadEmitter);

        TelemetryEvent event = new TelemetryEvent("status", "test-payload");
        eventStreamService.publish(event);

        // On subsequent publish, the dead emitter should have been removed and not called again
        eventStreamService.publish(event);

        // Verify deadEmitter send was only attempted once (during the first publish)
        verify(deadEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
