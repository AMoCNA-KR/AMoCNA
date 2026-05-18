package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 1: No raw metric values in GraphDB

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.*;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.sensor.IriFactory;
import net.jqwik.api.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: No raw metric values in GraphDB.
 *
 * For any SensorEvent of any type processed by KnowledgeBaseWriter, the SPARQL
 * update string produced must contain zero occurrences of "^^xsd:double" or
 * "^^xsd:float".
 *
 * Validates: Requirements 8.3, 8.4, 2.2
 */
class NoRawMetricValuesPropertyTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    // -------------------------------------------------------------------------
    // Spy subclass that captures all SPARQL strings passed to executeUpdate
    // -------------------------------------------------------------------------

    static class CapturingKnowledgeBaseWriter extends KnowledgeBaseWriter {

        final List<String> capturedSparql = new ArrayList<>();

        CapturingKnowledgeBaseWriter(Repository repository, IriFactory iriFactory) {
            super(repository, iriFactory);
        }

        @Override
        protected void executeUpdate(String sparql) throws KnowledgeBaseException {
            capturedSparql.add(sparql);
            super.executeUpdate(sparql);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers to build writer + registry for each test run
    // -------------------------------------------------------------------------

    private CapturingKnowledgeBaseWriter buildWriter() {
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE),
                null
        );
        IriFactory registry = new IriFactory(props);
        return new CapturingKnowledgeBaseWriter(repo, registry);
    }

    // -------------------------------------------------------------------------
    // Arbitraries for each event type
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

    @Property(tries = 100)
    void noRawMetricValuesInSparql(@ForAll @From("anySensorEvent") SensorEvent event)
            throws KnowledgeBaseException {

        CapturingKnowledgeBaseWriter writer = buildWriter();

        // Dispatch to the appropriate writer method based on event type
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

        // Assert: no raw floating-point type annotations in any captured SPARQL string
        for (String sparql : writer.capturedSparql) {
            assertThat(sparql)
                    .as("SPARQL must not contain ^^xsd:double")
                    .doesNotContain("^^xsd:double");
            assertThat(sparql)
                    .as("SPARQL must not contain ^^xsd:float")
                    .doesNotContain("^^xsd:float");
        }
    }
}
