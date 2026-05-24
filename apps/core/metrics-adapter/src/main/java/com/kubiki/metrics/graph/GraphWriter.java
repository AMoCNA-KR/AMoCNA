package com.kubiki.metrics.graph;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.model.util.Values;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class GraphWriter {
    private final String graphDbUrl;
    private final String repositoryId;
    private RemoteRepositoryManager manager;
    private Repository repository;

    private static final String CNEE_NS = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";
    private static final String HAS_STATE = CNEE_NS + "hasState";
    private static final String DETECTED_AT = CNEE_NS + "detectedAt";

    public GraphWriter(@Value("${graphdb.url:http://graphdb:7200}") String graphDbUrl,
                       @Value("${graphdb.repositoryId:amocna}") String repositoryId) {
        this.graphDbUrl = graphDbUrl;
        this.repositoryId = repositoryId;
    }

    @PostConstruct
    public void init() {
        try {
            manager = new RemoteRepositoryManager(graphDbUrl);
            manager.init();
            this.repository = manager.getRepository(repositoryId);
            log.info("Initialized GraphDB repository: {} at {}", repositoryId, graphDbUrl);
        } catch (Exception e) {
            log.error("Failed to initialize GraphDB repository manager at {}", graphDbUrl, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (manager != null) {
            log.info("Shutting down GraphDB repository manager");
            manager.shutDown();
        }
    }

    public void instantiateAnomaly(String targetResourceIri, String anomalyTypeIri) {
        if (repository == null) {
            log.error("Cannot instantiate anomaly: repository is not initialized");
            return;
        }

        String anomalyIri = anomalyTypeIri + "_" + UUID.randomUUID();
        Instant now = Instant.now();

        try (RepositoryConnection conn = repository.getConnection()) {
            try {
                conn.begin();

                conn.add(Values.iri(targetResourceIri),
                         Values.iri(HAS_STATE),
                         Values.iri(anomalyIri));

                conn.add(Values.iri(anomalyIri),
                         RDF.TYPE,
                         Values.iri(anomalyTypeIri));

                conn.add(Values.iri(anomalyIri),
                         Values.iri(DETECTED_AT),
                         Values.literal(now));

                conn.commit();
                log.info("Instantiated anomaly {} for resource {}", anomalyIri, targetResourceIri);
            } catch (Exception e) {
                if (conn.isActive()) {
                    conn.rollback();
                }
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to instantiate anomaly in GraphDB for resource {}", targetResourceIri, e);
        }
    }
}
