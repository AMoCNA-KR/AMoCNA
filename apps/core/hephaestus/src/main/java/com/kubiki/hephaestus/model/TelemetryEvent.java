package com.kubiki.hephaestus.model;

/**
 * A wrapper record for transmitting telemetry events to the frontend.
 */
public record TelemetryEvent(
        String type,      // "action", "status", or "graph.updates"
        Object payload,   // The underlying DTO payload
        long timestamp    // Unix epoch timestamp
) {
    public TelemetryEvent(String type, Object payload) {
        this(type, payload, System.currentTimeMillis());
    }
}
