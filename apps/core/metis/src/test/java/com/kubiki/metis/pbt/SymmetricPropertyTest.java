package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 5: Symmetric communicatesWith

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.sensor.IriFactory;
import net.jqwik.api.*;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.base.RepositoryConnectionWrapper;
import org.eclipse.rdf4j.repository.base.RepositoryWrapper;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: Symmetric communicatesWith
 *
 * For any RelationshipAssertedEvent with predicate cnee:communicatesWith and any pair of
 * entity IRIs A and B, the SPARQL update produced by KnowledgeBaseWriter must contain
 * both communicatesWith(A, B) and communicatesWith(B, A).
 *
 * Validates: Requirements 5.5
 */
class SymmetricPropertyTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";
    private static final String COMMUNICATES_WITH = CNEE + "communicatesWith";

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    /** Generates a valid absolute IRI with a random alphanumeric suffix. */
    @Provide
    Arbitrary<String> absoluteIris() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(suffix -> "http://example.org/" + suffix);
    }

    // -------------------------------------------------------------------------
    // Property
    // -------------------------------------------------------------------------

    /**
     * For any pair of entity IRIs A and B, asserting communicatesWith(A, B) must
     * produce a SPARQL update containing both communicatesWith(A, B) and
     * communicatesWith(B, A).
     *
     * Validates: Requirements 5.5
     */
    @Property(tries = 100)
    void communicatesWithIsSymmetric(
            @ForAll("absoluteIris") String iriA,
            @ForAll("absoluteIris") String iriB) throws KnowledgeBaseException {

        // Build an in-memory repository wrapped in a SPARQL-capturing proxy
        SailRepository memRepo = new SailRepository(new MemoryStore());
        memRepo.init();
        CapturingRepository capturingRepo = new CapturingRepository(memRepo);

        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE),
                null
        );
        IriFactory iriFactory = new IriFactory(props);
        KnowledgeBaseWriter writer = new KnowledgeBaseWriter(capturingRepo, iriFactory);

        RelationshipAssertedEvent event = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(iriA)
                .setPredicate(COMMUNICATES_WITH)
                .setObjectIri(iriB)
                .build();

        writer.assertRelationship(event);

        String capturedSparql = capturingRepo.getLastSparql();

        // Assert A communicatesWith B
        assertThat(capturedSparql)
                .as("SPARQL must contain communicatesWith(%s, %s)", iriA, iriB)
                .contains("<" + iriA + "> cnee:communicatesWith <" + iriB + ">");

        // Assert B communicatesWith A (symmetric direction)
        assertThat(capturedSparql)
                .as("SPARQL must contain communicatesWith(%s, %s) — symmetric direction", iriB, iriA)
                .contains("<" + iriB + "> cnee:communicatesWith <" + iriA + ">");

        memRepo.shutDown();
    }

    // -------------------------------------------------------------------------
    // CapturingRepository — intercepts SPARQL strings via RepositoryWrapper
    // -------------------------------------------------------------------------

    /**
     * A Repository wrapper that captures the SPARQL string passed to
     * prepareUpdate() on each connection.
     */
    static class CapturingRepository extends RepositoryWrapper {

        private volatile String lastSparql = "";

        CapturingRepository(Repository delegate) {
            super(delegate);
        }

        String getLastSparql() {
            return lastSparql;
        }

        @Override
        public RepositoryConnection getConnection() {
            return new CapturingConnection(super.getConnection(), this);
        }
    }

    /**
     * A RepositoryConnection wrapper that captures SPARQL update strings
     * before delegating to the real connection.
     */
    static class CapturingConnection extends RepositoryConnectionWrapper {

        private final CapturingRepository capturingRepository;

        CapturingConnection(RepositoryConnection delegate, CapturingRepository capturingRepository) {
            super(capturingRepository, delegate);
            this.capturingRepository = capturingRepository;
        }

        @Override
        public Update prepareUpdate(QueryLanguage ql, String update, String baseURI) {
            capturingRepository.lastSparql = update;
            return super.prepareUpdate(ql, update, baseURI);
        }

        @Override
        public Update prepareUpdate(String update) {
            capturingRepository.lastSparql = update;
            return super.prepareUpdate(update);
        }
    }
}
