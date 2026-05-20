package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 3: Functional state invariant

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.sensor.IriFactory;
import net.jqwik.api.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Functional State Invariant
 *
 * For any sequence of StateChangedEvent messages targeting the same resource_iri,
 * after applying each event to an in-memory RDF model via KnowledgeBaseWriter,
 * the model must contain exactly one cnee:hasCurrentState triple for that resource_iri —
 * regardless of how many state changes have been applied.
 *
 * Validates: Requirements 6.1, 6.2, 6.3
 */
class StateChangedPropertyTest {

    private static final String CNEE_NAMESPACE =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    private static final String HAS_STATE_IRI =
            CNEE_NAMESPACE + "hasState";

    /**
     * Builds a KnowledgeBaseWriter backed by the given in-memory repository.
     * IriFactory is constructed with a minimal MetisProperties stub.
     */
    private KnowledgeBaseWriter writerFor(Repository repo) {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE_NAMESPACE),
                null
        );
        IriFactory registry = new IriFactory(props);
        return new KnowledgeBaseWriter(repo, registry);
    }

    /**
     * Counts the number of cnee:hasCurrentState triples for the given resource IRI
     * in the repository.
     */
    private long countHasStateTriples(Repository repo, String resourceIri) {
        String sparql = """
                SELECT (COUNT(*) AS ?count)
                WHERE {
                  <%s> <%s> ?state .
                }
                """.formatted(resourceIri, HAS_STATE_IRI);

        try (RepositoryConnection conn = repo.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                if (result.hasNext()) {
                    String countStr = result.next().getValue("count").stringValue();
                    return Long.parseLong(countStr);
                }
            }
        }
        return 0L;
    }

    /**
     * Generates a non-empty list of StateChangedEvent for the same resource_iri,
     * with varying new_state_iri values (all CNEEOnt-namespaced).
     */
    @Provide
    Arbitrary<List<StateChangedEvent>> stateChangedEventLists() {
        // Fixed resource IRI — all events in the list target the same entity
        String resourceIri = CNEE_NAMESPACE + "TestResource_001";

        // Generate a non-empty list of distinct CNEEOnt state IRIs
        Arbitrary<String> stateIriArbitrary = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(fragment -> CNEE_NAMESPACE + "State_" + fragment);

        return stateIriArbitrary
                .list()
                .ofMinSize(1)
                .ofMaxSize(10)
                .map(stateIris -> stateIris.stream()
                        .map(stateIri -> StateChangedEvent.newBuilder()
                                .setResourceIri(resourceIri)
                                .setNewStateIri(stateIri)
                                .build())
                        .toList());
    }

    /**
     * Property: after applying each StateChangedEvent in the sequence to the same
     * in-memory repository, exactly one cnee:hasCurrentState triple exists for the
     * resource_iri — regardless of how many state changes have been applied.
     *
     * Validates: Requirements 6.1, 6.2, 6.3
     */
    @Property(tries = 100)
    void functionalStateInvariant(
            @ForAll @From("stateChangedEventLists") List<StateChangedEvent> events
    ) throws KnowledgeBaseException {
        // Fresh in-memory repository for each property trial
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();

        try {
            KnowledgeBaseWriter writer = writerFor(repo);
            String resourceIri = events.get(0).getResourceIri();

            for (StateChangedEvent event : events) {
                writer.changeState(event);

                long count = countHasStateTriples(repo, resourceIri);
                assertThat(count)
                        .as("After applying StateChangedEvent with new_state_iri='%s', " +
                                "expected exactly 1 cnee:hasState triple for resource '%s', but found %d",
                                event.getNewStateIri(), resourceIri, count)
                        .isEqualTo(1L);
            }
        } finally {
            repo.shutDown();
        }
    }
}
