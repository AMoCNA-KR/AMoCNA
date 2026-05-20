package com.kubiki.metis.knowledge;

import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.MetricMetadataRegisteredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.sensor.IriFactory;
import org.eclipse.rdf4j.common.exception.RDF4JException;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Translates {@link com.kubiki.metis.grpc.SensorEvent} messages into SPARQL
 * updates and executes them against the GraphDB knowledge base.
 *
 * <p>All writes are idempotent — duplicate triples are skipped via
 * {@code FILTER NOT EXISTS} guards or atomic {@code DELETE/INSERT WHERE}.
 */
@Service
public class KnowledgeBaseWriter {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseWriter.class);

    private final Repository repository;
    private final String cneeNamespace;
    private final String sparqlPrefixes;

    public KnowledgeBaseWriter(Repository repository, IriFactory iriFactory) {
        this.repository = repository;
        this.cneeNamespace = iriFactory.getCneeNamespace();
        this.sparqlPrefixes = """
                PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
                PREFIX cnee: <%s>
                """.formatted(cneeNamespace);
    }

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Inserts an entity discovered event into the knowledge base.
     *
     * <p>Validates that {@code ontology_type} starts with the CNEEOnt namespace.
     * Inserts mandatory triples ({@code rdf:type}, {@code cnee:resourceID},
     * {@code cnee:resourceName}) and any valid additional properties. Each
     * triple is guarded by its own {@code INSERT WHERE { FILTER NOT EXISTS }}
     * block for idempotency.
     *
     * @param event the entity discovered event
     * @throws KnowledgeBaseException if {@code ontology_type} is not a CNEEOnt IRI
     *                                or if the SPARQL update fails
     */
    public void insertEntity(EntityDiscoveredEvent event) throws KnowledgeBaseException {
        String resourceIri  = event.getResourceIri();
        String ontologyType = event.getOntologyType();
        String resourceId   = event.getResourceId();
        String resourceName = event.getResourceName();

        if (!isValidCneeIri(ontologyType)) {
            throw new KnowledgeBaseException(
                    "Rejected: ontology_type '" + ontologyType +
                    "' does not start with CNEEOnt namespace '" + cneeNamespace + "'");
        }

        // Build a single atomic INSERT with all mandatory triples + valid extra properties.
        // The FILTER NOT EXISTS guard on rdf:type ensures idempotency: re-applying the
        // same event has no effect once the entity exists. Extra properties are merged
        // by re-running with FILTER NOT EXISTS for the type triple — already-present
        // properties are just no-ops.
        StringBuilder triples = new StringBuilder();
        triples.append("  <").append(resourceIri).append("> rdf:type <").append(ontologyType).append("> ;\n");
        triples.append("    cnee:").append(CneeOntology.PROP_RESOURCE_ID)
                .append(" \"").append(escapeLiteral(resourceId)).append("\"^^xsd:string ;\n");
        triples.append("    cnee:").append(CneeOntology.PROP_RESOURCE_NAME)
                .append(" \"").append(escapeLiteral(resourceName)).append("\"^^xsd:string");

        boolean hasExtraProperties = false;
        for (Map.Entry<String, String> entry : event.getPropertiesMap().entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isEmpty() || value == null) {
                log.warn("Skipping malformed property entry: key='{}', value='{}'", key, value);
                continue;
            }
            if (!key.startsWith("http://") && !key.startsWith("https://")) {
                log.warn("Skipping property with non-absolute-IRI key: '{}'", key);
                continue;
            }

            triples.append(" ;\n    <").append(key).append("> \"")
                    .append(escapeLiteral(value)).append("\"^^xsd:string");
            hasExtraProperties = true;
        }
        triples.append(" .\n");

        String sparql = """
                %s
                INSERT {
                %s}
                WHERE {
                  FILTER NOT EXISTS { <%s> rdf:type <%s> }
                }
                """.formatted(sparqlPrefixes, triples, resourceIri, ontologyType);

        // Note: extra properties only get inserted on first sight of the entity.
        // Updates to extra properties are not supported via insertEntity — they
        // require an explicit StateChangedEvent or similar.
        if (hasExtraProperties) {
            log.debug("insertEntity for {} includes {} extra properties (only set on first insertion)",
                    resourceIri, event.getPropertiesMap().size());
        }

        executeUpdate(sparql);
    }

    public void assertRelationship(RelationshipAssertedEvent event) throws KnowledgeBaseException {
        String subjectIri = event.getSubjectIri();
        String predicate  = event.getPredicate();
        String objectIri  = event.getObjectIri();

        if (subjectIri == null || (!subjectIri.startsWith("http://") && !subjectIri.startsWith("https://"))) {
            throw new KnowledgeBaseException("subject_iri is not an absolute IRI: " + subjectIri);
        }
        if (objectIri == null || (!objectIri.startsWith("http://") && !objectIri.startsWith("https://"))) {
            throw new KnowledgeBaseException("object_iri is not an absolute IRI: " + objectIri);
        }
        if (!isValidCneeIri(predicate)) {
            throw new KnowledgeBaseException("predicate does not start with CNEEOnt namespace: " + predicate);
        }

        String inverseLocalName = inverseLocalName(predicate);
        String predicateLocalName = predicate.substring(cneeNamespace.length());
        String sparql;

        if (inverseLocalName != null) {
            // Pair predicate (contains/isPartOf, hosts/isHostedOn, communicatesWith) — emit both directions
            sparql = """
                    %s
                    INSERT {
                      <%s> cnee:%s <%s> .
                      <%s> cnee:%s <%s> .
                    }
                    WHERE {
                      FILTER NOT EXISTS { <%s> cnee:%s <%s> }
                    }
                    """.formatted(
                            sparqlPrefixes,
                            subjectIri, predicateLocalName, objectIri,
                            objectIri, inverseLocalName, subjectIri,
                            subjectIri, predicateLocalName, objectIri);
        } else {
            sparql = """
                    %s
                    INSERT { <%s> cnee:%s <%s> . }
                    WHERE { FILTER NOT EXISTS { <%s> cnee:%s <%s> } }
                    """.formatted(
                            sparqlPrefixes,
                            subjectIri, predicateLocalName, objectIri,
                            subjectIri, predicateLocalName, objectIri);
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
                %s
                DELETE {
                  <%s> cnee:%s ?oldState .
                }
                INSERT {
                  <%s> cnee:%s <%s> .
                }
                WHERE {
                  OPTIONAL { <%s> cnee:%s ?oldState }
                }
                """.formatted(
                        sparqlPrefixes,
                        resourceIri, CneeOntology.PROP_HAS_STATE,
                        resourceIri, CneeOntology.PROP_HAS_STATE, newStateIri,
                        resourceIri, CneeOntology.PROP_HAS_STATE);

        executeUpdate(sparql);
    }

    public void deleteEntity(EntityDeletedEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("deleteEntity: resource_iri must not be blank");
        }

        String sparql = """
                %s
                DELETE {
                  <%s> ?p ?o .
                  ?s ?p2 <%s> .
                }
                WHERE {
                  { <%s> ?p ?o }
                  UNION
                  { ?s ?p2 <%s> }
                }
                """.formatted(sparqlPrefixes, resourceIri, resourceIri, resourceIri, resourceIri);

        executeUpdate(sparql);
    }

    public void registerMetricMetadata(MetricMetadataRegisteredEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String endpointUrl = event.getEndpointUrl();
        String metricName  = event.getMetricName();

        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("registerMetricMetadata: resource_iri must not be blank");
        }

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

        // Derive metric IRI from the local name fragment of resource_iri.
        // If resource_iri = "http://.../CNEEOnt#Pod_default_my-pod" and metric_name = "cpu",
        // then metric_iri = "cnee:Pod_default_my-pod_cpu" — a valid local name, not a re-encoded full IRI.
        String resourceLocalName = localNameOf(resourceIri);
        if (resourceLocalName == null) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: cannot derive local name from resource_iri: " + resourceIri);
        }
        String metricIri = cneeNamespace + encodeFragment(resourceLocalName) + "_" + encodeFragment(metricName);

        String sparql = """
                %s
                INSERT {
                  <%s>  cnee:%s "%s"^^xsd:anyURI .
                  <%s>  rdf:type cnee:%s .
                  <%s>  cnee:%s "%s"^^xsd:string .
                  <%s>  cnee:%s <%s> .
                }
                WHERE {
                  FILTER NOT EXISTS { <%s> cnee:%s <%s> }
                }
                """.formatted(
                        sparqlPrefixes,
                        resourceIri, CneeOntology.PROP_METRICS_ENDPOINT, endpointUrl,
                        metricIri, CneeOntology.CLASS_METRIC,
                        metricIri, CneeOntology.PROP_RESOURCE_NAME, escapeLiteral(metricName),
                        resourceIri, CneeOntology.PROP_EMITS_TELEMETRY, metricIri,
                        resourceIri, CneeOntology.PROP_EMITS_TELEMETRY, metricIri);

        executeUpdate(sparql);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the local name of the inverse / symmetric predicate to also assert,
     * or {@code null} if the predicate has no defined inverse and only the single
     * triple should be inserted.
     */
    private String inverseLocalName(String predicate) {
        String localName = predicate.substring(cneeNamespace.length());
        return switch (localName) {
            case CneeOntology.PROP_CONTAINS          -> CneeOntology.PROP_IS_PART_OF;
            case CneeOntology.PROP_IS_PART_OF        -> CneeOntology.PROP_CONTAINS;
            case CneeOntology.PROP_HOSTS             -> CneeOntology.PROP_IS_HOSTED_ON;
            case CneeOntology.PROP_IS_HOSTED_ON      -> CneeOntology.PROP_HOSTS;
            case CneeOntology.PROP_COMMUNICATES_WITH -> CneeOntology.PROP_COMMUNICATES_WITH;
            default                                  -> null;
        };
    }

    /**
     * Opens a {@link RepositoryConnection}, prepares and executes the given SPARQL update,
     * then closes the connection. Any {@link RDF4JException} is wrapped in a
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

    private boolean isValidCneeIri(String iri) {
        return iri != null && iri.startsWith(cneeNamespace);
    }

    /**
     * Returns the local name (last fragment after {@code #} or {@code /}) of an IRI,
     * or {@code null} if the IRI has no parseable local part.
     */
    private String localNameOf(String iri) {
        if (iri == null || iri.isBlank()) return null;
        int hash  = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int idx   = Math.max(hash, slash);
        if (idx < 0 || idx >= iri.length() - 1) return null;
        return iri.substring(idx + 1);
    }

    private String escapeLiteral(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    private String encodeFragment(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
