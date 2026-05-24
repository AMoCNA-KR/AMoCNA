package com.kubiki.metrics.prometheus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class PrometheusClient {
    private final WebClient webClient;

    public PrometheusClient(WebClient.Builder builder, @Value("${prometheus.url:http://prometheus:9090}") String prometheusUrl) {
        log.info("Initializing PrometheusClient with URL: {}", prometheusUrl);
        this.webClient = builder.baseUrl(prometheusUrl).build();
    }

    public Mono<Double> queryScalar(String query) {
        log.debug("Executing Prometheus query: {}", query);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToMono(PrometheusResponse.class)
                .flatMap(response -> {
                    if (!"success".equals(response.getStatus())) {
                        log.warn("Prometheus query returned non-success status: {}", response.getStatus());
                        return Mono.empty();
                    }

                    if (response.getData() == null || 
                        response.getData().getResult() == null || 
                        response.getData().getResult().isEmpty()) {
                        log.debug("Prometheus query returned no results for query: {}", query);
                        return Mono.empty();
                    }
                    
                    try {
                        List<Object> value = response.getData().getResult().get(0).getValue();
                        if (value != null && value.size() > 1) {
                            String valStr = value.get(1).toString();
                            return Mono.just(Double.parseDouble(valStr));
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse Prometheus result value for query: {}", query, e);
                        return Mono.error(e);
                    }
                    
                    return Mono.empty();
                })
                .doOnError(e -> log.error("Error communicating with Prometheus for query: {}", query, e));
    }

    @Data
    public static class PrometheusResponse {
        private String status;
        private PrometheusData data;
    }

    @Data
    public static class PrometheusData {
        private String resultType;
        private List<PrometheusResult> result;
    }

    @Data
    public static class PrometheusResult {
        private java.util.Map<String, String> metric;
        private List<Object> value;
    }
}
