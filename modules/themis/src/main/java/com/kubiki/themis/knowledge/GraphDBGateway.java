package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class GraphDBGateway {
    private final Repository repository;
    private static final String NAMESPACE = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#";

    public GraphDBGateway(ThemisProperties properties) {
        this.repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
    }

    @PostConstruct
    public void init() {
        this.repository.init();
    }

    public List<String> findActionsForResource(String resourceId) {
        String sparql = "PREFIX moa: <" + NAMESPACE + "> " +
                        "SELECT ?action WHERE { " +
                        "  ?action moa:targetsEntity <" + resourceId + "> . " +
                        "  ?action a moa:AutonomicAction . " +
                        "}";
        
        List<String> actions = new ArrayList<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    actions.add(result.next().getValue("action").stringValue());
                }
            }
        }
        return actions;
    }

    @PreDestroy
    public void shutDown() {
        repository.shutDown();
    }
}
