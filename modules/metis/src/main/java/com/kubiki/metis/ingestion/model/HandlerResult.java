package com.kubiki.metis.ingestion.model;

import com.kubiki.palamedes.grpc.ChangeKind;

/**
 * Result returned by a {@link com.kubiki.metis.ingestion.handler.SensorEventHandler}
 * after processing a single {@code SensorEvent}.
 *
 * <p>Use the static factories {@link #success}, {@link #failure}, and
 * {@link #graphDbFailure} rather than the canonical record constructor.
 */
public record HandlerResult(
        boolean success,
        String resourceIri,
        String ontologyType,
        ChangeKind changeKind,
        String failureReason,
        boolean graphDbFailed
) {

    /**
     * Creates a successful result carrying the IRI, ontology type, and change kind
     * that will be forwarded to Palamedes.
     */
    public static HandlerResult success(String resourceIri, String ontologyType, ChangeKind changeKind) {
        return new HandlerResult(true, resourceIri, ontologyType, changeKind, null, false);
    }

    /**
     * Creates a validation failure result. All IRI / type / kind fields are {@code null}.
     *
     * @param reason human-readable description of why the event could not be processed
     */
    public static HandlerResult failure(String reason) {
        return new HandlerResult(false, null, null, null, reason, false);
    }

    /**
     * Creates a GraphDB failure result indicating the knowledge base was unavailable.
     * All IRI / type / kind fields are {@code null}.
     *
     * @param reason human-readable description of the GraphDB error
     */
    public static HandlerResult graphDbFailure(String reason) {
        return new HandlerResult(false, null, null, null, reason, true);
    }
}
