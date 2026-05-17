package com.kubiki.metis.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.kubiki.metis.grpc.*;
import com.kubiki.palamedes.grpc.ReasonerServiceGrpc;
import com.kubiki.palamedes.grpc.TriggerResponse;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link SensorIngestionGrpcService}.
 *
 * <p>Uses an in-process gRPC server (via {@code grpc.server.inProcessName=test}) and
 * WireMock to simulate the GraphDB HTTP endpoint. The Palamedes gRPC stub is mocked
 * to avoid requiring a real Palamedes server.
 *
 * <p><b>Requirements: 1.1, 1.11–1.13, 9.1–9.5, 14.1, 14.4</b>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.inProcessName=test-sensor-ingestion",
                "grpc.server.port=-1"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(initializers = SensorIngestionGrpcServiceIT.WireMockInitializer.class)
class SensorIngestionGrpcServiceIT {

    private static final String CNEE_NS =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    private static final String SPARQL_UPDATE_PATH =
            "/repositories/test/statements";

    // WireMock server is started once for the entire test class
    static WireMockServer wireMockServer;

    // In-process gRPC channel and stub
    private ManagedChannel channel;
    private SensorIngestionServiceGrpc.SensorIngestionServiceBlockingStub stub;

    // -------------------------------------------------------------------------
    // WireMock lifecycle
    // -------------------------------------------------------------------------

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        // Create in-process channel to the running gRPC server
        channel = InProcessChannelBuilder
                .forName("test-sensor-ingestion")
                .directExecutor()
                .build();
        stub = SensorIngestionServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // Test configuration — override Palamedes beans to avoid real connection
    // -------------------------------------------------------------------------

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        ReasonerServiceGrpc.ReasonerServiceBlockingStub mockReasonerStub() {
            ReasonerServiceGrpc.ReasonerServiceBlockingStub mockStub =
                    mock(ReasonerServiceGrpc.ReasonerServiceBlockingStub.class);
            when(mockStub.triggerUpdate(any())).thenReturn(
                    TriggerResponse.newBuilder()
                            .setAccepted(true)
                            .setTrackingId("test-tracking-id")
                            .build());
            return mockStub;
        }

        @Bean
        @Primary
        ManagedChannel mockPalamedesChannel() {
            return mock(ManagedChannel.class);
        }
    }

    // -------------------------------------------------------------------------
    // ApplicationContextInitializer — injects WireMock port into Spring context
    // -------------------------------------------------------------------------

    static class WireMockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            // WireMock server must be started before the context is refreshed
            if (wireMockServer == null) {
                wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
                wireMockServer.start();
                WireMock.configureFor("localhost", wireMockServer.port());
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    applicationContext,
                    "wiremock.port=" + wireMockServer.port(),
                    "wiremock.grpc.port=0"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Test cases
    // -------------------------------------------------------------------------

    /**
     * Valid {@code EntityDiscoveredEvent} batch → {@code accepted = true},
     * {@code processed_count = 1}, WireMock received one SPARQL update request.
     *
     * <p><b>Requirements: 1.1, 9.1, 9.3, 9.4</b>
     */
    @Test
    void validEntityDiscoveredBatch_acceptedTrueProcessedCountOne() {
        // Stub GraphDB protocol check (RDF4J calls /protocol before SPARQL updates)
        wireMockServer.stubFor(get(urlEqualTo("/protocol"))
                .willReturn(aResponse().withStatus(200).withBody("12")));
        // Stub GraphDB SPARQL update endpoint to return 204 No Content
        wireMockServer.stubFor(post(urlEqualTo(SPARQL_UPDATE_PATH))
                .willReturn(aResponse().withStatus(204)));

        EntityDiscoveredEvent entityEvent = EntityDiscoveredEvent.newBuilder()
                .setResourceIri("http://example.org/pod-1")
                .setOntologyType(CNEE_NS + "ExecutionUnit")
                .setResourceId("pod-1")
                .setResourceName("my-pod")
                .build();

        SensorEvent sensorEvent = SensorEvent.newBuilder()
                .setEntityDiscovered(entityEvent)
                .build();

        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-valid-001")
                .addEvents(sensorEvent)
                .build();

        IngestResponse response = stub.ingestBatch(batch);

        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getCorrelationId()).isEqualTo("corr-valid-001");

        // Verify WireMock received exactly one SPARQL update request
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(SPARQL_UPDATE_PATH)));
    }

    /**
     * Empty batch → {@code accepted = true}, {@code processed_count = 0}.
     *
     * <p><b>Requirements: 1.12, 9.5</b>
     */
    @Test
    void emptyBatch_acceptedTrueProcessedCountZero() {
        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-empty-001")
                .build();

        IngestResponse response = stub.ingestBatch(batch);

        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getProcessedCount()).isEqualTo(0);
        assertThat(response.getCorrelationId()).isEqualTo("corr-empty-001");

        // No SPARQL update should have been sent
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(SPARQL_UPDATE_PATH)));
    }

    /**
     * GraphDB returns 503 → gRPC response status {@code UNAVAILABLE}.
     *
     * <p><b>Requirements: 14.1, 14.4</b>
     */
    @Test
    void graphDbReturns503_grpcStatusUnavailable() {
        // Stub GraphDB protocol check to succeed
        wireMockServer.stubFor(get(urlEqualTo("/protocol"))
                .willReturn(aResponse().withStatus(200).withBody("12")));
        // Stub GraphDB SPARQL update endpoint to return 503 Service Unavailable
        wireMockServer.stubFor(post(urlEqualTo(SPARQL_UPDATE_PATH))
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        EntityDiscoveredEvent entityEvent = EntityDiscoveredEvent.newBuilder()
                .setResourceIri("http://example.org/pod-2")
                .setOntologyType(CNEE_NS + "ExecutionUnit")
                .setResourceId("pod-2")
                .setResourceName("my-pod-2")
                .build();

        SensorEvent sensorEvent = SensorEvent.newBuilder()
                .setEntityDiscovered(entityEvent)
                .build();

        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-503-001")
                .addEvents(sensorEvent)
                .build();

        assertThatThrownBy(() -> stub.ingestBatch(batch))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode())
                            .isEqualTo(io.grpc.Status.Code.UNAVAILABLE);
                });
    }

    /**
     * {@code correlation_id} longer than 128 characters → {@code accepted = false}.
     *
     * <p><b>Requirements: 1.2, 1.11</b>
     */
    @Test
    void correlationIdExceeds128Chars_acceptedFalse() {
        String longCorrelationId = "x".repeat(129);

        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId(longCorrelationId)
                .build();

        IngestResponse response = stub.ingestBatch(batch);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getCorrelationId()).isEqualTo(longCorrelationId);

        // No SPARQL update should have been sent
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(SPARQL_UPDATE_PATH)));
    }
}
