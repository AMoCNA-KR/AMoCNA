package com.kubiki.metis.knowledge;

import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.MetricMetadataRegisteredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.StateChangedEvent;
import org.eclipse.rdf4j.common.exception.RDF4JException;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class KnowledgeBaseWriter {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseWriter.class);

    private static final String CNEE_NAMESPACE =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    private static final String SPARQL_PREFIXES =
            "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>\n" +
            "PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#>\n";

    private final Repository repository;
    private final OntologyRegistry ontologyRegistry;

    public KnowledgeBaseWriter(Repository repository, OntologyRegistry ontologyRegistry) {
        this.repository = repository;
        this.ontologyRegistry = ontologyRegistry;
    }

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Inserts an entity discovered event into the knowledge base.
     *
     * <p>Validates that {@code ontology_type} starts with the CNEEOnt namespace.
     * Inserts mandatory triples (rdf:type, cnee:resourceID, cnee:resourceName) and
     * any valid additional properties. Each triple is guarded by its own
     * {@code INSERT WHERE { FILTER NOT EXISTS }} block for idempotency.
     *
     * @param event the entity discovered event
     * @throws KnowledgeBaseException if {@code ontology_type} is not a CNEEOnt IRI,
     *                                or if a SPARQL update fails
     */
    public void insertEntity(EntityDiscoveredEvent event) throws KnowledgeBaseException {
        String resourceIri  = event.getResourceIri();
        String ontologyType = event.getOntologyType();
        String resourceId   = event.getResourceId();
        String resourceName = event.getResourceName();

        // Validate ontology_type namespace
        if (!isValidCneeIri(ontologyType)) {
            throw new KnowledgeBaseException(
                    "Rejected: ontology_type '" + ontologyType +
                    "' does not start with CNEEOnt namespace '" + CNEE_NAMESPACE + "'");
        }

        StringBuilder sb = new StringBuilder();

        // Triple 1: rdf:type
        sb.append(SPARQL_PREFIXES);
        sb.append("INSERT { <").append(resourceIri).append("> rdf:type <").append(ontologyType).append("> . }\n");
        sb.append("WHERE { FILTER NOT EXISTS { <").append(resourceIri).append("> rdf:type <").append(ontologyType).append("> } }\n");

        // Triple 2: cnee:resourceID
        sb.append(";\n");
        sb.append(SPARQL_PREFIXES);
        sb.append("INSERT { <").append(resourceIri).append("> cnee:resourceID \"").append(escapeLiteral(resourceId)).append("\"^^xsd:string . }\n");
        sb.append("WHERE { FILTER NOT EXISTS { <").append(resourceIri).append("> cnee:resourceID ?any } }\n");

        // Triple 3: cnee:resourceName
        sb.append(";\n");
        sb.append(SPARQL_PREFIXES);
        sb.append("INSERT { <").append(resourceIri).append("> cnee:resourceName \"").append(escapeLiteral(resourceName)).append("\"^^xsd:string . }\n");
        sb.append("WHERE { FILTER NOT EXISTS { <").append(resourceIri).append("> cnee:resourceName ?any } }\n");

        // Additional properties — skip malformed entries
        for (Map.Entry<String, String> entry : event.getPropertiesMap().entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();

            // Skip null/empty key, non-absolute-IRI key, or null value
            if (key == null || key.isEmpty() || value == null) {
                log.warn("Skipping malformed property entry: key='{}', value='{}'", key, value);
                continue;
            }
            if (!key.startsWith("http://") && !key.startsWith("https://")) {
                log.warn("Skipping property with non-absolute-IRI key: '{}'", key);
                continue;
            }

            sb.append(";\n");
            sb.append(SPARQL_PREFIXES);
            sb.append("INSERT { <").append(resourceIri).append("> <").append(key).append("> \"").append(escapeLiteral(value)).append("\"^^xsd:string . }\n");
            sb.append("WHERE { FILTER NOT EXISTS { <").append(resourceIri).append("> <").append(key).append("> ?any } }\n");
        }

        executeUpdate(sb.toString());
    }

    public void assertRelationship(RelationshipAssertedEvent event) throws KnowledgeBaseException {
        String subjectIri = event.getSubjectIri();
        String predicate  = event.getPredicate();
        String objectIri  = event.getObjectIri();

        // Validate subject and object are absolute IRIs
        if (subjectIri == null || (!subjectIri.startsWith("http://") && !subjectIri.startsWith("https://"))) {
            throw new KnowledgeBaseException(
                    "subject_iri is not an absolute IRI: " + subjectIri);
        }
        if (objectIri == null || (!objectIri.startsWith("http://") && !objectIri.startsWith("https://"))) {
            throw new KnowledgeBaseException(
                    "object_iri is not an absolute IRI: " + objectIri);
        }

        // Validate predicate is in CNEEOnt namespace
        if (!isValidCneeIri(predicate)) {
            throw new KnowledgeBaseException(
                    "predicate does not start with CNEEOnt namespace: " + predicate);
        }

        String sparql;

        if (predicate.equals(CNEE_NAMESPACE + "contains")) {
            sparql = buildPrefixes() +
                    "INSERT {\n" +
                    "  <" + subjectIri + "> cnee:contains <" + objectIri + "> .\n" +
                    "  <" + objectIri + "> cnee:isPartOf <" + subjectIri + "> .\n" +
                    "}\n" +
                    "WHERE {\n" +
                    "  FILTER NOT EXISTS { <" + subjectIri + "> cnee:contains <" + objectIri + "> }\n" +
                    "}";

        } else if (predicate.equals(CNEE_NAMESPACE + "isPartOf")) {
            sparql = buildPrefixes() +
                    "INSERT {\n" +
                    "  <" + subjectIri + "> cnee:isPartOf <" + objectIri + "> .\n" +
                    "  <" + objectIri + "> cnee:contains <" + subjectIri + "> .\n" +
                    "}\n" +
                    "WHERE {\n" +
                    "  FILTER NOT EXISTS { <" + subjectIri + "> cnee:isPartOf <" + objectIri + "> }\n" +
                    "}";

        } else if (predicate.equals(CNEE_NAMESPACE + "hosts")) {
            sparql = buildPrefixes() +
                    "INSERT {\n" +
                    "  <" + subjectIri + "> cnee:hosts <" + objectIri + "> .\n" +
                    "  <" + objectIri + "> cnee:isHostedOn <" + subjectIri + "> .\n" +
                    "}\n" +
                    "WHERE {\n" +
                    "  FILTER NOT EXISTS { <" + subjectIri + "> cnee:hosts <" + objectIri + "> }\n" +
                    "}";

        } else if (predicate.equals(CNEE_NAMESPACE + "isHostedOn")) {
            sparql = buildPrefixes() +
                    "INSERT {\n" +
                    "  <" + subjectIri + "> cnee:isHostedOn <" + objectIri + "> .\n" +
                    "  <" + objectIri + "> cnee:hosts <" + subjectIri + "> .\n" +
                    "}\n" +
                    "WHERE {\n" +
                    "  FILTER NOT EXISTS { <" + subjectIri + "> cnee:isHostedOn <" + objectIri + "> }\n" +
                    "}";

        } else if (predicate.equals(CNEE_NAMESPACE + "communicatesWith")) {
            sparql = buildPrefixes() +
                    "INSERT {\n" +
                    "  <" + subjectIri + "> cnee:communicatesWith <" + objectIri + "> .\n" +
                    "  <" + objectIri + "> cnee:communicatesWith <" + subjectIri + "> .\n" +
                    "}\n" +
                    "WHERE {\n" +
                    "  FILTER NOT EXISTS { <" + subjectIri + "> cnee:communicatesWith <" + objectIri + "> }\n" +
                    "}";

        } else {
            // Any other CNEEOnt predicate: insert only the single triple
            sparql = buildPrefixes() +
                    "INSERT {\n" +
                    "  <" + subjectIri + "> <" + predicate + "> <" + objectIri + "> .\n" +
                    "}\n" +
                    "WHERE {\n" +
                    "  FILTER NOT EXISTS { <" + subjectIri + "> <" + predicate + "> <" + objectIri + "> }\n" +
                    "}";
        }

        executeUpdate(sparql);
    }

    public void changeState(StateChangedEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String newStateIri = event.getNewStateIri();

        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("changeState: resource_iri must not be blank");
        }
        if (newStateIri == null || newStateIri.isBlank()) {
            throw new KnowledgeBaseException("changeState: new_state_iri must not be blank");
        }
        if (!isValidCneeIri(newStateIri)) {
            throw new KnowledgeBaseException(
                    "changeState: new_state_iri must start with CNEEOnt namespace, got: " + newStateIri);
        }

        String sparql = """
                PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
                PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#>
                DELETE {
                  <%s> cnee:hasCurrentState ?oldState .
                }
                INSERT {
                  <%s> cnee:hasCurrentState <%s> .
                }
                WHERE {
                  OPTIONAL { <%s> cnee:hasCurrentState ?oldState }
                }
                """.formatted(resourceIri, resourceIri, newStateIri, resourceIri);

        executeUpdate(sparql);
    }

    public void deleteEntity(EntityDeletedEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("deleteEntity: resource_iri must not be blank");
        }

        String sparql = """
                PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
                PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#>
                DELETE {
                  <%s> ?p ?o .
                  ?s ?p2 <%s> .
                }
                WHERE {
                  { <%s> ?p ?o }
                  UNION
                  { ?s ?p2 <%s> }
                }
                """.formatted(resourceIri, resourceIri, resourceIri, resourceIri);

        executeUpdate(sparql);
    }

    public void registerMetricMetadata(MetricMetadataRegisteredEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String endpointUrl = event.getEndpointUrl();
        String metricName  = event.getMetricName();

        // Validate resource_iri is non-blank
        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: resource_iri must not be blank");
        }

        // Validate endpoint_url is a valid absolute URI
        try {
            java.net.URI uri = new java.net.URI(endpointUrl);
            if (!uri.isAbsolute()) {
                throw new KnowledgeBaseException(
                        "registerMetricMetadata: endpoint_url is not an absolute URI: " + endpointUrl);
            }
        } catch (java.net.URISyntaxException e) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: endpoint_url is not a valid URI: " + endpointUrl, e);
        }

        // Derive metric_iri: cnee:<encodeFragment(resource_iri)>_<encodeFragment(metric_name)>
        String metricIri = CNEE_NAMESPACE + encodeFragment(resourceIri) + "_" + encodeFragment(metricName);

        String sparql = String.format("""
                PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
                PREFIX cnee: <http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#>
                INSERT {
                  <%s>  cnee:metricsEndpoint "%s"^^xsd:anyURI .
                  <%s>  rdf:type              cnee:Metric .
                  <%s>  cnee:resourceName     "%s"^^xsd:string .
                  <%s>  cnee:emitsTelemetry   <%s> .
                }
                WHERE {
                  FILTER NOT EXISTS { <%s> cnee:emitsTelemetry <%s> }
                }
                """,
                resourceIri, endpointUrl,
                metricIri,
                metricIri, metricName,
                resourceIri, metricIri,
                resourceIri, metricIri);

        executeUpdate(sparql);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the standard SPARQL prefix declarations used in all update queries.
     */
    private String buildPrefixes() {
        return SPARQL_PREFIXES;
    }

    /**
     * Opens a {@link RepositoryConnection}, prepares and executes the given SPARQL update,
     * then closes the connection. Any {@link RDF4JException} (including
     * {@link RepositoryException} and {@code HTTPUpdateExecutionException}) is wrapped in a
     * {@link KnowledgeBaseException} and logged at ERROR level together with the SPARQL string.
     *
     * <p>Declared {@code protected} to allow test subclasses to intercept SPARQL strings.
     */
    protected void executeUpdate(String sparql) throws KnowledgeBaseException {
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.prepareUpdate(sparql).execute();
        } catch (RDF4JException e) {
            log.error("SPARQL update failed. Query: {}", sparql, e);
            throw new KnowledgeBaseException("SPARQL update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns {@code true} iff the given IRI starts with the CNEEOnt namespace prefix.
     */
    private boolean isValidCneeIri(String iri) {
        return iri != null && iri.startsWith(CNEE_NAMESPACE);
    }

    /**
     * Escapes special characters in a SPARQL string literal to prevent injection.
     * Escapes backslash, double-quote, and newline characters.
     */
    private String escapeLiteral(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    /**
     * Percent-encodes characters that are not valid in an IRI fragment using
     * {@link URLEncoder}, then replaces {@code +} with {@code %20} so that
     * spaces are encoded as {@code %20} rather than {@code +}.
     */
    private String encodeFragment(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

}
