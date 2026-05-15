package com.kubiki.metis.pbt;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.knowledge.OntologyRegistry;
import net.jqwik.api.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * // Feature: metis-monitor-module, Property 2: Mandatory triples and ontology type conformance
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 13.1, 13.2
 */
class EntityDiscoveredPropertyTest {

    private static final String CNEE_NAMESPACE =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    // -------------------------------------------------------------------------
    // SPARQL-capturing subclass of KnowledgeBaseWriter
    // -------------------------------------------------------------------------

    /**
     * Subclass that overrides the protected {@code executeUpdate} method to
     * capture every SPARQL string before delegating to the real implementation.
     */
    static class CapturingKnowledgeBaseWriter extends KnowledgeBaseWriter {

        private final List<String> capturedSparql = new ArrayList<>();

        CapturingKnowledgeBaseWriter(Repository repository, OntologyRegistry ontologyRegistry) {
            super(repository, ontologyRegistry);
        }

        @Override
        protected void executeUpdate(String sparql) throws KnowledgeBaseException {
            capturedSparql.add(sparql);
            super.executeUpdate(sparql);
        }

        List<String> getCapturedSparql() {
            return capturedSparql;
        }

        String getAllCapturedSparql() {
            return String.join("\n", capturedSparql);
        }
    }

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    /** Generates a non-empty alphanumeric string of length 1–20. */
    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    /** Generates a valid CNEEOnt-namespaced ontology type IRI. */
    @Provide
    Arbitrary<String> cneeOntologyTypes() {
        return nonEmptyStrings().map(fragment -> CNEE_NAMESPACE + fragment);
    }

    /** Generates a valid absolute resource IRI. */
    @Provide
    Arbitrary<String> resourceIris() {
        return nonEmptyStrings().map(s -> "http://example.org/resource/" + s);
    }

    // -------------------------------------------------------------------------
    // Property test
    // -------------------------------------------------------------------------

    /**
     * Property 2: Mandatory triples and ontology type conformance.
     *
     * For any valid EntityDiscoveredEvent with a CNEEOnt-namespaced ontology_type,
     * non-empty resource_iri, resource_id, and resource_name, the SPARQL update
     * produced by KnowledgeBaseWriter must contain:
     * - exactly one rdf:type triple whose object IRI begins with the CNEEOnt namespace,
     * - exactly one cnee:resourceID data property triple,
     * - exactly one cnee:resourceName data property triple.
     *
     * // Feature: metis-monitor-module, Property 2: Mandatory triples and ontology type conformance
     *
     * Validates: Requirements 4.1, 4.2, 4.3, 13.1, 13.2
     */
    @Property(tries = 100)
    void mandatoryTriplesAndOntologyTypeConformance(
            @ForAll("resourceIris") String resourceIri,
            @ForAll("cneeOntologyTypes") String ontologyType,
            @ForAll("nonEmptyStrings") String resourceId,
            @ForAll("nonEmptyStrings") String resourceName
    ) throws KnowledgeBaseException {
        // Arrange: set up a real in-memory repository and a capturing writer
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();

        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE_NAMESPACE),
                new MetisProperties.Palamedes("localhost", 50051), null
        );
        OntologyRegistry ontologyRegistry = new OntologyRegistry(props);
        CapturingKnowledgeBaseWriter writer =
                new CapturingKnowledgeBaseWriter(repo, ontologyRegistry);

        // Build the event
        EntityDiscoveredEvent event = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(resourceIri)
                .setOntologyType(ontologyType)
                .setResourceId(resourceId)
                .setResourceName(resourceName)
                .build();

        // Act
        writer.insertEntity(event);

        // Assert: join all captured SPARQL into one string for analysis
        String allSparql = writer.getAllCapturedSparql();

        // 1. Exactly one rdf:type triple with a CNEEOnt-namespaced object.
        //    In the INSERT body the triple ends with " ." so we count
        //    "rdf:type <CNEEOnt#...> ." occurrences (INSERT body only, not FILTER clause).
        long rdfTypeCount = countInsertTriples(allSparql, "rdf:type <" + CNEE_NAMESPACE);
        assertThat(rdfTypeCount)
                .as("Expected exactly one rdf:type triple with CNEEOnt-namespaced object in SPARQL:\n%s",
                        allSparql)
                .isEqualTo(1);

        // 2. Exactly one cnee:resourceID triple in the INSERT body.
        long resourceIdCount = countInsertTriples(allSparql, "cnee:resourceID");
        assertThat(resourceIdCount)
                .as("Expected exactly one cnee:resourceID triple in SPARQL:\n%s", allSparql)
                .isEqualTo(1);

        // 3. Exactly one cnee:resourceName triple in the INSERT body.
        long resourceNameCount = countInsertTriples(allSparql, "cnee:resourceName");
        assertThat(resourceNameCount)
                .as("Expected exactly one cnee:resourceName triple in SPARQL:\n%s", allSparql)
                .isEqualTo(1);

        repo.shutDown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Counts occurrences of {@code substring} in {@code text}.
     */
    private long countOccurrences(String text, String substring) {
        if (text == null || substring == null || substring.isEmpty()) return 0;
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }

    /**
     * Counts how many times a predicate/object pattern appears as a triple in an
     * INSERT body (i.e., the line ends with " ." after the pattern).
     *
     * <p>The SPARQL produced by {@code KnowledgeBaseWriter.insertEntity} uses one
     * {@code INSERT … WHERE { FILTER NOT EXISTS … }} block per triple. Each block
     * contains the predicate twice: once in the INSERT body (ending with " .") and
     * once in the FILTER clause (ending with " }"). This method counts only the
     * INSERT body occurrences by looking for the pattern followed (anywhere on the
     * same logical line) by " ." before the next newline.
     */
    private long countInsertTriples(String sparql, String predicatePattern) {
        if (sparql == null || predicatePattern == null) return 0;
        long count = 0;
        int idx = 0;
        while ((idx = sparql.indexOf(predicatePattern, idx)) != -1) {
            // Find the end of this "line" (next newline or end of string)
            int lineEnd = sparql.indexOf('\n', idx);
            if (lineEnd == -1) lineEnd = sparql.length();
            String line = sparql.substring(idx, lineEnd);
            // INSERT body triples end with " ." (possibly with trailing whitespace)
            if (line.contains(" .")) {
                count++;
            }
            idx += predicatePattern.length();
        }
        return count;
    }
}
