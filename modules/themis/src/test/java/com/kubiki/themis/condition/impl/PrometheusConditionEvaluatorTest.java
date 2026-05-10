package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PrometheusConditionEvaluator Tests")
class PrometheusConditionEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThemisProperties properties = new ThemisProperties(
            null,
            new ThemisProperties.Ontology("http://example.org/moam#"),
            new ThemisProperties.Prometheus("http://prometheus:9090")
    );
    private PrometheusConditionEvaluator evaluator;
    private MockRestServiceServer server;

    static Stream<Arguments> resultCases() throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        return Stream.of(
                Arguments.of("result not empty", om.writeValueAsString(Map.of(
                        "status", "success",
                        "data", Map.of("result", List.of(Map.of("metric", Map.of(), "value", List.of(1, "1"))))
                )), true),
                Arguments.of("result empty", om.writeValueAsString(Map.of(
                        "status", "success",
                        "data", Map.of("result", List.of())
                )), false)
        );
    }

    static Stream<Arguments> missingFieldCases() throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        return Stream.of(
                Arguments.of("missing status", om.writeValueAsString(Map.of(
                        "data", Map.of("result", List.of())
                )), "Missing 'status' field"),
                Arguments.of("missing data", om.writeValueAsString(Map.of(
                        "status", "success"
                )), "Missing 'data' field"),
                Arguments.of("missing result", om.writeValueAsString(Map.of(
                        "status", "success",
                        "data", Map.of()
                )), "Missing 'result' field")
        );
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.prometheus().url());
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        evaluator = new PrometheusConditionEvaluator(restClient, properties, objectMapper);
    }

    @Test
    @DisplayName("should support PrometheusCondition IRI")
    void shouldSupportPrometheusCondition() {
        assertTrue(evaluator.supports(SimpleValueFactory.getInstance().createIRI("http://example.org/moam#PrometheusCondition")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resultCases")
    void shouldEvaluateBasedOnResultPresence(String name, String responseJson, boolean expected) {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#PrometheusCondition"),
                "up");

        assertEquals(expected, evaluator.evaluate(condition));
    }

    @Test
    @DisplayName("should throw ConditionEvaluationException when Prometheus request fails")
    void shouldThrowExceptionOnError() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withServerError());

        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#PrometheusCondition"),
                "up");
        assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
    }

    @Test
    @DisplayName("should throw ConditionEvaluationException when RestClient is null")
    void shouldThrowExceptionWhenRestClientIsNull() {
        PrometheusConditionEvaluator nullClientEvaluator = new PrometheusConditionEvaluator(null, properties, objectMapper);
        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#PrometheusCondition"),
                "up");
        ConditionEvaluationException exception = assertThrows(ConditionEvaluationException.class, () -> nullClientEvaluator.evaluate(condition));
        assertEquals("Prometheus RestClient is not configured. Check 'themis.prometheus.url' property.", exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingFieldCases")
    void shouldThrowExceptionWhenRequiredFieldIsMissing(String name, String responseJson, String expectedMessage) {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moam#PrometheusCondition"),
                "up");

        ConditionEvaluationException exception = assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
        assertTrue(exception.getMessage().contains(expectedMessage));
    }
}