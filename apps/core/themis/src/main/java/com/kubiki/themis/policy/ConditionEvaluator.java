package com.kubiki.themis.policy;

import com.kubiki.themis.config.ThemisProperties;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConditionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);
    private static final String MOAM_NS = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#";

    private final ThemisProperties properties;
    private final RestClient restClient;

    public ConditionEvaluator(ThemisProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public boolean evaluatePreConditions(String actionId) {
        return evaluateConditions(actionId, "moam:hasPreCondition", "pre");
    }

    public boolean evaluatePostConditions(String actionId) {
        return evaluateConditions(actionId, "moam:hasPostCondition", "post");
    }

    private boolean evaluateConditions(String actionId, String property, String logPrefix) {
        log.info("Evaluating {}-conditions for action: {}", logPrefix, actionId);
        String actionIri = properties.graphdb().actionsNamespace() + actionId;

        List<String> conditions = fetchConditions(actionIri, property);
        if (conditions.isEmpty()) {
            log.info("No {}-conditions found for action: {}", logPrefix, actionId);
            return true;
        }

        for (String conditionQuery : conditions) {
            boolean result;
            if (isPromQL(conditionQuery)) {
                result = evaluatePromQL(conditionQuery);
            } else {
                result = evaluateSparqlAsk(conditionQuery);
            }

            if (!result) {
                log.warn("{}-condition failed for action {}: {}", logPrefix, actionId, conditionQuery);
                return false;
            }
        }

        return true;
    }

    private List<String> fetchConditions(String actionIri, String property) {
        List<String> conditions = new ArrayList<>();
        String sparql = String.format(
                "PREFIX moam: <%s> " +
                "SELECT ?query WHERE { " +
                "  <%s> %s ?cond . " +
                "  ?cond moam:policyQueryString ?query . " +
                "}", MOAM_NS, actionIri, property);

        RemoteRepositoryManager manager = RemoteRepositoryManager.getInstance(properties.graphdb().url());
        try {
            manager.init();
            Repository repo = manager.getRepository(properties.graphdb().repositoryId());
            try (RepositoryConnection conn = repo.getConnection()) {
                TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, sparql);
                try (TupleQueryResult result = query.evaluate()) {
                    while (result.hasNext()) {
                        conditions.add(result.next().getValue("query").stringValue());
                    }
                }
            }
        } finally {
            manager.shutDown();
        }
        return conditions;
    }

    private boolean isPromQL(String query) {
        String upper = query.toUpperCase();
        return !upper.contains("ASK") && !upper.contains("SELECT") && !upper.contains("PREFIX");
    }

    private boolean evaluateSparqlAsk(String sparql) {
        RemoteRepositoryManager manager = RemoteRepositoryManager.getInstance(properties.graphdb().url());
        try {
            manager.init();
            Repository repo = manager.getRepository(properties.graphdb().repositoryId());
            try (RepositoryConnection conn = repo.getConnection()) {
                BooleanQuery query = conn.prepareBooleanQuery(QueryLanguage.SPARQL, sparql);
                return query.evaluate();
            }
        } finally {
            manager.shutDown();
        }
    }

    private boolean evaluatePromQL(String promql) {
        try {
            var response = restClient.get()
                    .uri(properties.prometheus().url() + "/api/v1/query?query={query}", promql)
                    .retrieve()
                    .body(PrometheusResponse.class);

            return response != null && response.data() != null && !response.data().result().isEmpty();
        } catch (Exception e) {
            log.error("Error evaluating PromQL: {}", promql, e);
            return false;
        }
    }

    private record PrometheusResponse(String status, PrometheusData data) {}
    private record PrometheusData(String resultType, List<Object> result) {}
}
