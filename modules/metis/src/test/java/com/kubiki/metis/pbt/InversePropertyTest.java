package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 4: Inverse property consistency

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.sensor.IriFactory;
import net.jqwik.api.*;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.base.RepositoryConnectionWrapper;
import org.eclipse.rdf4j.repository.base.RepositoryWrapper;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.io.File;

/**
 * Property 4: Inverse property consistency.
 *
 * For any RelationshipAssertedEvent with predicate cnee:contains, cnee:isPartOf,
 * cnee:hosts, or cnee:isHostedOn, and for any subject and object IRIs, the SPARQL
 * update produced by KnowledgeBaseWriter must contain both the asserted triple and
 * its inverse triple.
 *
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4
 */
class InversePropertyTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    /**
     * Inverse predicate pairs: [asserted predicate, expected inverse predicate]
     */
    private static final String[][] INVERSE_PAIRS = {
        { CNEE + "contains",   CNEE + "isPartOf"   },
        { CNEE + "isPartOf",   CNEE + "contains"   },
        { CNEE + "hosts",      CNEE + "isHostedOn"  },
        { CNEE + "isHostedOn", CNEE + "hosts"       }
    };

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

    /** Picks one of the four inverse predicate pairs. */
    @Provide
    Arbitrary<String[]> inversePredicate() {
        return Arbitraries.of(INVERSE_PAIRS);
    }

    // -------------------------------------------------------------------------
    // Property
    // -------------------------------------------------------------------------

    /**
     * For any subject/object IRI pair and any predicate from the four inverse
     * predicates, the SPARQL update string produced by KnowledgeBaseWriter must
     * contain both the asserted triple and its inverse triple.
     *
     * Validates: Requirements 5.1, 5.2, 5.3, 5.4
     */
    @Property(tries = 100)
    void inversePropertyConsistency(
            @ForAll("absoluteIris") String subjectIri,
            @ForAll("absoluteIris") String objectIri,
            @ForAll("inversePredicate") String[] predicatePair
    ) throws KnowledgeBaseException {
        String predicate        = predicatePair[0];
        String inversePredicate = predicatePair[1];

        // Build an in-memory repository wrapped in a SPARQL-capturing proxy
        SailRepository memRepo = new SailRepository(new MemoryStore());
        memRepo.init();
        CapturingRepository capturingRepo = new CapturingRepository(memRepo);

        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Palamedes("localhost", 50051), null
        );
        IriFactory iriFactory = new IriFactory(props);
        KnowledgeBaseWriter writer = new KnowledgeBaseWriter(capturingRepo, iriFactory);

        RelationshipAssertedEvent event = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(subjectIri)
                .setPredicate(predicate)
                .setObjectIri(objectIri)
                .build();

        writer.assertRelationship(event);

        String capturedSparql = capturingRepo.getLastSparql();

        // Assert the asserted triple is present in the SPARQL
        assertTriplePresent(capturedSparql, subjectIri, predicate, objectIri);

        // Assert the inverse triple is present in the SPARQL
        assertTriplePresent(capturedSparql, objectIri, inversePredicate, subjectIri);

        memRepo.shutDown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Asserts that the SPARQL string contains a triple pattern for the given
     * subject IRI, predicate IRI, and object IRI. The predicate is matched by
     * its local name (fragment after '#') using the cnee: prefix.
     */
    private void assertTriplePresent(String sparql, String subjectIri, String predicateIri, String objectIri) {
        String predicateLocalName = predicateIri.substring(predicateIri.lastIndexOf('#') + 1);
        String predicateCnee = "cnee:" + predicateLocalName;

        // The SPARQL uses cnee: prefix for CNEEOnt predicates
        // Pattern: <subjectIri> cnee:localName <objectIri>
        String triplePattern = "<" + subjectIri + "> " + predicateCnee + " <" + objectIri + ">";

        if (!sparql.contains(triplePattern)) {
            throw new AssertionError(
                    "Expected SPARQL to contain triple: " + triplePattern +
                    "\nActual SPARQL:\n" + sparql);
        }
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
