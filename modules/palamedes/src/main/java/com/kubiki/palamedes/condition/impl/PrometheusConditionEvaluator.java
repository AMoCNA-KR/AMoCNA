package com.kubiki.palamedes.condition.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.palamedes.condition.ConditionEvaluator;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.constants.OntologyConstants;
import com.kubiki.palamedes.exception.ConditionEvaluationException;
import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.kubiki.palamedes.constants.OntologyConstants.CLASS_PROMETHEUS_CONDITION;

@Component
public class PrometheusConditionEvaluator implements ConditionEvaluator {

    private static final String PROMETHEUS_QUERY_PATH = "/api/v1/query";
    private static final String QUERY_PARAM = "query";
    private static final String RESPONSE_STATUS_SUCCESS = "success";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_RESULT = "result";

    private static final String ERROR_STATUS = "Prometheus query failed with status: ";
    private static final String ERROR_EMPTY_BODY = "Prometheus returned empty body";
    private static final String ERROR_NOT_SUCCESSFUL = "Prometheus query was not successful";
    private static final String ERROR_PARSE = "Error parsing Prometheus response: ";
    private static final String ERROR_EVALUATE = "Error evaluating Prometheus condition: ";
    private static final String ERROR_NOT_CONFIGURED = "Prometheus RestClient is not configured. Check 'themis.prometheus.url' property.";

    private static final String ERROR_MISSING_STATUS = "Missing 'status' field in Prometheus response";
    private static final String ERROR_MISSING_DATA = "Missing 'data' field in Prometheus response";
    private static final String ERROR_MISSING_RESULT = "Missing 'result' field in Prometheus response";

    private final RestClient restClient;
    private final PalamedesProperties properties;
    private final ObjectMapper objectMapper;

    public PrometheusConditionEvaluator(@Nullable @Qualifier("prometheusRestClient") RestClient restClient,
                                        PalamedesProperties properties,
                                        ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(IRI conditionType) {
        String prometheusCondition = properties.ontology().moamNamespace() + CLASS_PROMETHEUS_CONDITION;
        return prometheusCondition.equals(conditionType.stringValue());
    }

    @Override
    public boolean evaluate(ActionData.Condition condition) {
        if (restClient == null) {
            throw new ConditionEvaluationException(ERROR_NOT_CONFIGURED);
        }
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(PROMETHEUS_QUERY_PATH)
                            .queryParam(QUERY_PARAM, condition.policy())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        throw new ConditionEvaluationException(ERROR_STATUS + resp.getStatusCode());
                    })
                    .body(String.class);

            if (responseBody == null) {
                throw new ConditionEvaluationException(ERROR_EMPTY_BODY);
            }

            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode statusNode = response.get(FIELD_STATUS);
            if (statusNode == null) {
                throw new ConditionEvaluationException(ERROR_MISSING_STATUS);
            }
            if (!RESPONSE_STATUS_SUCCESS.equals(statusNode.asText())) {
                throw new ConditionEvaluationException(ERROR_NOT_SUCCESSFUL);
            }

            JsonNode dataNode = response.get(FIELD_DATA);
            if (dataNode == null) {
                throw new ConditionEvaluationException(ERROR_MISSING_DATA);
            }

            JsonNode result = dataNode.get(FIELD_RESULT);
            if (result == null) {
                throw new ConditionEvaluationException(ERROR_MISSING_RESULT);
            }
            return result.isArray() && !result.isEmpty();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ConditionEvaluationException(ERROR_PARSE + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof ConditionEvaluationException) {
                throw e;
            }
            throw new ConditionEvaluationException(ERROR_EVALUATE + e.getMessage(), e);
        }
    }
}