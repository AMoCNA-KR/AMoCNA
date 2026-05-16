package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 7: Idempotency across all event types

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.*;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.sensor.IriFactory;
import net.jqwik.api.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 7: Idempotency across all event types.
 *
 * For any SensorEvent of any type, applying the event to an in-memory RDF model once
 * and then applying the identical event a second time must produce the same model state
 * as applying it once. Specifically, the number of triples in the model after two
 * identical applications must equal the number after one application.
 *
 * Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 4.5, 5.7, 8.6
 */
class IdempotencyPropertyTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    // -------------------------------------------------------------------------
    // Helper: build a KnowledgeBaseWriter backed by the given repository
    // -------------------------------------------------------------------------

    private KnowledgeBaseWriter writerFor(Repository repo) {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Palamedes("localhost", 50051), null
        );
        IriFactory registry = new IriFactory(props);
        return new KnowledgeBaseWriter(repo, registry);
    }

    /** Returns the total number of triples currently in the repository. */
    private long countTriples(Repository repo) {
        try (RepositoryConnection conn = repo.getConnection()) {
            return conn.size();
        }
    }

    /**
     * Applies the given SensorEvent to the writer, dispatching to the correct
     * KnowledgeBaseWriter method based on the event type.
     */
    private void applyEvent(KnowledgeBaseWriter writer, SensorEvent event)
            throws KnowledgeBaseException {
        switch (event.getEventCase()) {
            case ENTITY_DISCOVERED ->
                    writer.insertEntity(event.getEntityDiscovered());
            case RELATIONSHIP_ASSERTED ->
                    writer.assertRelationship(event.getRelationshipAsserted());
            case STATE_CHANGED ->
                    writer.changeState(event.getStateChanged());
            case ENTITY_DELETED ->
                    writer.deleteEntity(event.getEntityDeleted());
            case METRIC_METADATA_REGISTERED ->
                    writer.registerMetricMetadata(event.getMetricMetadataRegistered());
            default ->
                    throw new IllegalArgumentException("Unexpected event case: " + event.getEventCase());
        }
    }

    // -------------------------------------------------------------------------
    // Arbitraries — one per event type, all producing valid events
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<SensorEvent> entityDiscoveredEvents() {
        Arbitrary<String> resourceIri = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://example.org/" + s);

        Arbitrary<String> ontologyType = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> CNEE + s);

        Arbitrary<String> resourceId = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20);

        Arbitrary<String> resourceName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(resourceIri, ontologyType, resourceId, resourceName)
                .as((iri, type, id, name) -> SensorEvent.newBuilder()
                        .setEntityDiscovered(
                                EntityDiscoveredEvent.newBuilder()
                                        .setResourceIri(iri)
                                        .setOntologyType(type)
                                        .setResourceId(id)
                                        .setResourceName(name)
                                        .build()
                        ).build());
    }

    @Provide
    Arbitrary<SensorEvent> relationshipAssertedEvents() {
        Arbitrary<String> subjectIri = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://example.org/subject/" + s);

        Arbitrary<String> objectIri = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://example.org/object/" + s);

        Arbitrary<String> predicate = Arbitraries.of(
                CNEE + "contains",
                CNEE + "isPartOf",
                CNEE + "hosts",
                CNEE + "isHostedOn",
                CNEE + "communicatesWith",
                CNEE + "relatedTo"
        );

        return Combinators.combine(subjectIri, objectIri, predicate)
                .as((subj, obj, pred) -> SensorEvent.newBuilder()
                        .setRelationshipAsserted(
                                RelationshipAssertedEvent.newBuilder()
                                        .setSubjectIri(subj)
                                        .setObjectIri(obj)
                                        .setPredicate(pred)
                                        .build()
                        ).build());
    }

    @Provide
    Arbitrary<SensorEvent> stateChangedEvents() {
        Arbitrary<String> resourceIri = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://example.org/" + s);

        Arbitrary<String> newStateIri = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> CNEE + "State_" + s);

        return Combinators.combine(resourceIri, newStateIri)
                .as((iri, state) -> SensorEvent.newBuilder()
                        .setStateChanged(
                                StateChangedEvent.newBuilder()
                                        .setResourceIri(iri)
                                        .setNewStateIri(state)
                                        .build()
                        ).build());
    }

    @Provide
    Arbitrary<SensorEvent> entityDeletedEvents() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://example.org/" + s)
                .map(iri -> SensorEvent.newBuilder()
                        .setEntityDeleted(
                                EntityDeletedEvent.newBuilder()
                                        .setResourceIri(iri)
                                        .build()
                        ).build());
    }

    @Provide
    Arbitrary<SensorEvent> metricMetadataRegisteredEvents() {
        Arbitrary<String> resourceIri = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://example.org/" + s);

        Arbitrary<String> metricName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20);

        Arbitrary<String> endpointUrl = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "http://metrics.example.org/" + s);

        return Combinators.combine(resourceIri, metricName, endpointUrl)
                .as((iri, name, url) -> SensorEvent.newBuilder()
                        .setMetricMetadataRegistered(
                                MetricMetadataRegisteredEvent.newBuilder()
                                        .setResourceIri(iri)
                                        .setMetricName(name)
                                        .setEndpointUrl(url)
                                        .build()
                        ).build());
    }

    @Provide
    Arbitrary<SensorEvent> anySensorEvent() {
        return Arbitraries.oneOf(
                entityDiscoveredEvents(),
                relationshipAssertedEvents(),
                stateChangedEvents(),
                entityDeletedEvents(),
                metricMetadataRegisteredEvents()
        );
    }

    // -------------------------------------------------------------------------
    // Property test
    // -------------------------------------------------------------------------

    /**
     * Property 7: Idempotency across all event types.
     *
     * Applies the same SensorEvent twice to a fresh in-memory repository.
     * The triple count after the second application must equal the count after
     * the first application — i.e., re-sending the same event has no additional
     * effect on the knowledge base.
     *
     * Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 4.5, 5.7, 8.6
     */
    @Property(tries = 100)
    void idempotencyAcrossAllEventTypes(
            @ForAll @From("anySensorEvent") SensorEvent event
    ) throws KnowledgeBaseException {
        // Fresh in-memory repository for each property trial
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();

        try {
            KnowledgeBaseWriter writer = writerFor(repo);

            // First application
            applyEvent(writer, event);
            long countAfterFirst = countTriples(repo);

            // Second application of the identical event
            applyEvent(writer, event);
            long countAfterSecond = countTriples(repo);

            assertThat(countAfterSecond)
                    .as("Applying event '%s' a second time must not change the triple count " +
                            "(expected %d, got %d). Idempotency violated.",
                            event.getEventCase(), countAfterFirst, countAfterSecond)
                    .isEqualTo(countAfterFirst);

        } finally {
            repo.shutDown();
        }
    }
}
