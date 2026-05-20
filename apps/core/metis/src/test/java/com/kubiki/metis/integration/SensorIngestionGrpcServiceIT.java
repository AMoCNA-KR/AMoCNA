package com.kubiki.metis.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.kubiki.metis.grpc.*;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.*;
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
import static org.mockito.Mockito.mock;

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
    private static final String SPARQL_UPDATE_PATH = "/repositories/test/statements";

    static WireMockServer wireMockServer;
    private Object channel;
    private SensorIngestionServiceGrpc.SensorIngestionServiceBlockingStub stub;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        var ch = InProcessChannelBuilder.forName("test-sensor-ingestion").directExecutor().build();
        channel = ch;
        stub = SensorIngestionServiceGrpc.newBlockingStub(ch);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            ((io.grpc.ManagedChannel) channel).shutdownNow();
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        org.springframework.amqp.rabbit.core.RabbitTemplate mockRabbitTemplate() {
            return mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
        }
    }

    static class WireMockInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            if (wireMockServer == null) {
                wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
                wireMockServer.start();
                WireMock.configureFor("localhost", wireMockServer.port());
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(ctx,
                    "wiremock.port=" + wireMockServer.port(), "wiremock.grpc.port=0");
        }
    }

    @Test
    void validEntityDiscoveredBatch_acceptedTrueProcessedCountOne() {
        wireMockServer.stubFor(get(urlEqualTo("/protocol")).willReturn(aResponse().withStatus(200).withBody("12")));
        wireMockServer.stubFor(post(urlEqualTo(SPARQL_UPDATE_PATH)).willReturn(aResponse().withStatus(204)));

        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-valid-001")
                .addEvents(SensorEvent.newBuilder().setEntityDiscovered(
                        EntityDiscoveredEvent.newBuilder()
                                .setResourceIri("http://example.org/pod-1")
                                .setOntologyType(CNEE_NS + "ExecutionUnit")
                                .setResourceId("pod-1")
                                .setResourceName("my-pod").build()).build())
                .build();

        IngestResponse response = stub.ingestBatch(batch);
        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getCorrelationId()).isEqualTo("corr-valid-001");
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(SPARQL_UPDATE_PATH)));
    }

    @Test
    void emptyBatch_acceptedTrueProcessedCountZero() {
        SensorBatch batch = SensorBatch.newBuilder().setCorrelationId("corr-empty-001").build();
        IngestResponse response = stub.ingestBatch(batch);
        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getProcessedCount()).isEqualTo(0);
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(SPARQL_UPDATE_PATH)));
    }

    @Test
    void graphDbReturns503_grpcStatusUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/protocol")).willReturn(aResponse().withStatus(200).withBody("12")));
        wireMockServer.stubFor(post(urlEqualTo(SPARQL_UPDATE_PATH)).willReturn(aResponse().withStatus(503)));

        SensorBatch batch = SensorBatch.newBuilder()
                .setCorrelationId("corr-503")
                .addEvents(SensorEvent.newBuilder().setEntityDiscovered(
                        EntityDiscoveredEvent.newBuilder()
                                .setResourceIri("http://example.org/pod-2")
                                .setOntologyType(CNEE_NS + "ExecutionUnit")
                                .setResourceId("pod-2")
                                .setResourceName("p2").build()).build())
                .build();

        assertThatThrownBy(() -> stub.ingestBatch(batch))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(io.grpc.Status.Code.UNAVAILABLE));
    }

    @Test
    void correlationIdExceeds128Chars_acceptedFalse() {
        SensorBatch batch = SensorBatch.newBuilder().setCorrelationId("x".repeat(129)).build();
        IngestResponse response = stub.ingestBatch(batch);
        assertThat(response.getAccepted()).isFalse();
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(SPARQL_UPDATE_PATH)));
    }
}
