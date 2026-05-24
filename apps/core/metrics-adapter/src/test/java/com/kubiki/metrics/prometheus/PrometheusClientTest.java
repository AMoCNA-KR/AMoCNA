package com.kubiki.metrics.prometheus;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@WireMockTest
class PrometheusClientTest {

    private PrometheusClient prometheusClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        WebClient.Builder builder = WebClient.builder();
        prometheusClient = new PrometheusClient(builder, wmInfo.getHttpBaseUrl());
    }

    @Test
    void queryScalar_Success() {
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .withQueryParam("query", equalTo("up"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1716550000.000,\"1.0\"]}]}}")));

        Mono<Double> result = prometheusClient.queryScalar("up");

        StepVerifier.create(result)
                .expectNext(1.0)
                .verifyComplete();
    }

    @Test
    void queryScalar_NoResults() {
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}")));

        Mono<Double> result = prometheusClient.queryScalar("up");

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void queryScalar_NonSuccessStatus() {
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"invalid query\"}")));

        Mono<Double> result = prometheusClient.queryScalar("invalid_query");

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void queryScalar_Error() {
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .willReturn(aResponse().withStatus(500)));

        Mono<Double> result = prometheusClient.queryScalar("up");

        StepVerifier.create(result)
                .verifyError();
    }
}
