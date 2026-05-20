package com.kubiki.palamedes.model;

/**
 * Message received from Metis via the {@code amocna.graph.updates} RabbitMQ queue
 * when the knowledge base has been updated.
 *
 * @param resourceIri   fully-qualified IRI of the affected resource
 * @param ontologyType  fully-qualified CNEEOnt class IRI
 * @param changeKind    one of: CREATED, UPDATED, STATE_CHANGED, DELETED
 * @param correlationId correlation identifier from the originating sensor batch
 */
public record GraphUpdateMessage(
        String resourceIri,
        String ontologyType,
        String changeKind,
        String correlationId
) {}
