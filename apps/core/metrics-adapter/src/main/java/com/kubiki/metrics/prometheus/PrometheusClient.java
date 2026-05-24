package com.kubiki.metrics.prometheus;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class PrometheusClient {
    private final WebClient webClient;

    public PrometheusClient(WebClient.Builder builder, @Value("${prometheus.url:http://prometheus:9090}") String prometheusUrl) {
        this.webClient = builder.baseUrl(prometheusUrl).build();
    }

    public Mono<Double> queryScalar(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToMono(PrometheusResponse.class)
                .map(response -> {
                    if ("success".equals(response.getStatus()) && 
                        response.getData() != null && 
                        response.getData().getResult() != null && 
                        !response.getData().getResult().isEmpty()) {
                        
                        // Handle both 'vector' and 'scalar' result types if possible, 
                        // but following the provided mapping logic which assumes a list of results with values.
                        // Assuming resultType is 'vector' or similar where result is a list of objects with a 'value' field.
                        List<Object> value = response.getData().getResult().get(0).getValue();
                        if (value != null && value.size() > 1) {
                            return Double.parseDouble(value.get(1).toString());
                        }
                    }
                    return 0.0;
                })
                .onErrorReturn(0.0);
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
