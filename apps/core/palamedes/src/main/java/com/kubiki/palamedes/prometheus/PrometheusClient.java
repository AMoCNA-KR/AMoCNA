package com.kubiki.palamedes.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST client for querying Prometheus via its HTTP API.
 */
@Component
public class PrometheusClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PrometheusClient(PalamedesProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.prometheus().url())
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Executes an instant query against Prometheus and returns the results.
     *
     * @param query PromQL query string
     * @return list of results, each containing metric labels and the numeric value
     */
    public List<QueryResult> query(String query) {
        try {
            String response = restClient.get()
                    .uri("/api/v1/query?query={query}", query)
                    .retrieve()
                    .body(String.class);

            return parseResponse(response);
        } catch (Exception e) {
            log.error("Prometheus query failed [query={}]: {}", query, e.getMessage());
            return List.of();
        }
    }

    private List<QueryResult> parseResponse(String json) {
        List<QueryResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!"success".equals(root.path("status").asText())) {
                log.warn("Prometheus returned non-success status: {}", root.path("status").asText());
                return results;
            }

            JsonNode resultArray = root.path("data").path("result");
            for (JsonNode item : resultArray) {
                Map<String, String> labels = new HashMap<>();
                item.path("metric").fields().forEachRemaining(
                        entry -> labels.put(entry.getKey(), entry.getValue().asText()));

                JsonNode valueArray = item.path("value");
                double value = valueArray.get(1).asDouble();

                results.add(new QueryResult(labels, value));
            }
        } catch (Exception e) {
            log.error("Failed to parse Prometheus response: {}", e.getMessage());
        }
        return results;
    }

    /**
     * A single result from a Prometheus instant query.
     */
    public record QueryResult(Map<String, String> labels, double value) {}
}
