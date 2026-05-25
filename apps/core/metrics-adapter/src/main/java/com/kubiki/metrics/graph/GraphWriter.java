package com.kubiki.metrics.graph;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class GraphWriter {
  private final String graphDbUrl;
  private final String repositoryId;
  private final SparqlRepository sparqlRepository;
  private RemoteRepositoryManager manager;
  private Repository repository;

  public GraphWriter(@Value("${graphdb.url:http://graphdb:7200}") String graphDbUrl,
      @Value("${graphdb.repositoryId:amocna}") String repositoryId,
      SparqlRepository sparqlRepository) {
    this.graphDbUrl = graphDbUrl;
    this.repositoryId = repositoryId;
    this.sparqlRepository = sparqlRepository;
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
    String timestamp = Instant.now().toString();

    try (RepositoryConnection conn = repository.getConnection()) {
      String updateQuery = sparqlRepository.instantiateAnomaly(targetResourceIri, anomalyIri, anomalyTypeIri, timestamp);
      conn.prepareUpdate(updateQuery).execute();
      log.info("Instantiated anomaly {} for resource {}", anomalyIri, targetResourceIri);
    } catch (Exception e) {
      log.error("Failed to instantiate anomaly in GraphDB for resource {}", targetResourceIri, e);
    }
  }
}
