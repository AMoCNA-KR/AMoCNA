package com.kubiki.metis.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kubiki.metis.notification.PalamedesNotifier;
import com.kubiki.palamedes.grpc.ChangeKind;
import com.kubiki.palamedes.grpc.ReasonerServiceGrpc;
import com.kubiki.palamedes.grpc.ResourceUpdate;
import com.kubiki.palamedes.grpc.TriggerResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link PalamedesNotifier}.
 *
 * <p>Uses an in-process gRPC server (no network I/O) to simulate the Palamedes
 * {@code ReasonerService}. This avoids external dependencies while exercising the
 * real gRPC channel and stub wiring.
 *
 * <p><b>Requirements: 10.1–10.5</b>
 */
class PalamedesNotifierIT {

    private static final String SERVER_NAME = "palamedes-notifier-it-" + System.nanoTime();

    private static final String CNEE_NS =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    /** Captures the last {@link ResourceUpdate} received by the fake server. */
    private final AtomicReference<ResourceUpdate> capturedRequest = new AtomicReference<>();

    /** Controls whether the fake server returns success or throws an error. */
    private volatile boolean serverShouldFail = false;

    private Server inProcessServer;
    private ManagedChannel channel;
    private PalamedesNotifier notifier;

    @BeforeEach
    void setUp() throws IOException {
        capturedRequest.set(null);
        serverShouldFail = false;

        // Build an in-process gRPC server implementing ReasonerService
        inProcessServer = InProcessServerBuilder
                .forName(SERVER_NAME)
                .directExecutor()
                .addService(new FakeReasonerService())
                .build()
                .start();

        // Build a channel to the in-process server
        channel = InProcessChannelBuilder
                .forName(SERVER_NAME)
                .directExecutor()
                .build();

        ReasonerServiceGrpc.ReasonerServiceBlockingStub stub =
                ReasonerServiceGrpc.newBlockingStub(channel);

        notifier = new PalamedesNotifier(stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow();
        inProcessServer.shutdownNow();
        inProcessServer.awaitTermination();
    }

    // -------------------------------------------------------------------------
    // Test 1: Successful TriggerUpdate — ResourceUpdate fields match expected values
    // -------------------------------------------------------------------------

    /**
     * When Palamedes responds successfully, the {@link ResourceUpdate} received by
     * the server must contain exactly the values passed to
     * {@link PalamedesNotifier#notify}.
     *
     * <p><b>Requirements: 10.1, 10.2</b>
     */
    @Test
    void successfulTriggerUpdate_resourceUpdateFieldsMatchExpected() {
        // Arrange
        String resourceIri   = CNEE_NS + "Pod_my-pod-abc";
        String ontologyType  = CNEE_NS + "ExecutionUnit";
        ChangeKind changeKind = ChangeKind.CREATED;
        String correlationId = "corr-12345";

        // Act — must not throw
        assertThatCode(() ->
                notifier.notify(resourceIri, ontologyType, changeKind, correlationId)
        ).doesNotThrowAnyException();

        // Assert — server received the correct ResourceUpdate
        ResourceUpdate received = capturedRequest.get();
        assertThat(received).isNotNull();
        assertThat(received.getResourceIri()).isEqualTo(resourceIri);
        assertThat(received.getOntologyType()).isEqualTo(ontologyType);
        assertThat(received.getChangeKind()).isEqualTo(changeKind);
        assertThat(received.getCorrelationId()).isEqualTo(correlationId);
    }

    /**
     * Verifies that all four {@link ChangeKind} values are forwarded correctly.
     *
     * <p><b>Requirements: 10.2</b>
     */
    @Test
    void successfulTriggerUpdate_allChangeKindsForwardedCorrectly() {
        String resourceIri  = CNEE_NS + "Pod_test";
        String ontologyType = CNEE_NS + "ExecutionUnit";
        String correlationId = "corr-ck-test";

        for (ChangeKind kind : List.of(
                ChangeKind.CREATED,
                ChangeKind.UPDATED,
                ChangeKind.STATE_CHANGED,
                ChangeKind.DELETED)) {

            capturedRequest.set(null);
            notifier.notify(resourceIri, ontologyType, kind, correlationId);

            ResourceUpdate received = capturedRequest.get();
            assertThat(received).isNotNull();
            assertThat(received.getChangeKind()).isEqualTo(kind);
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: Palamedes throws exception — notifier logs ERROR, does not rethrow
    // -------------------------------------------------------------------------

    /**
     * When the Palamedes gRPC call fails with an exception, {@link PalamedesNotifier}
     * must:
     * <ul>
     *   <li>NOT rethrow the exception (so {@code IngestResponse.accepted} remains {@code true})</li>
     *   <li>Log the failure at ERROR level including the {@code correlationId}</li>
     * </ul>
     *
     * <p><b>Requirements: 10.4</b>
     */
    @Test
    void palamedesThrowsException_notifierLogsErrorAndDoesNotRethrow() {
        // Arrange — configure the fake server to return a gRPC error
        serverShouldFail = true;

        String correlationId = "corr-error-test";

        // Attach a log appender to capture PalamedesNotifier's log output
        Logger notifierLogger = (Logger) LoggerFactory.getLogger(PalamedesNotifier.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        notifierLogger.addAppender(listAppender);

        try {
            // Act — must NOT throw even though Palamedes fails
            assertThatCode(() ->
                    notifier.notify(
                            CNEE_NS + "Pod_fail",
                            CNEE_NS + "ExecutionUnit",
                            ChangeKind.DELETED,
                            correlationId)
            ).doesNotThrowAnyException();

            // Assert — an ERROR log entry was emitted containing the correlationId
            List<ILoggingEvent> errorLogs = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .toList();

            assertThat(errorLogs)
                    .as("Expected at least one ERROR log from PalamedesNotifier")
                    .isNotEmpty();

            assertThat(errorLogs)
                    .as("ERROR log must contain the correlationId")
                    .anyMatch(e -> e.getFormattedMessage().contains(correlationId));

        } finally {
            notifierLogger.detachAppender(listAppender);
        }
    }

    /**
     * When Palamedes fails, the notifier must not rethrow — this ensures the
     * caller (SensorIngestionGrpcService) can still return {@code accepted = true}.
     *
     * <p><b>Requirements: 10.4, 10.5</b>
     */
    @Test
    void palamedesThrowsException_callerCanStillReturnAcceptedTrue() {
        serverShouldFail = true;

        // The notifier must swallow the exception — no exception propagates to the caller
        assertThatCode(() ->
                notifier.notify(
                        CNEE_NS + "Pod_swallow",
                        CNEE_NS + "ExecutionUnit",
                        ChangeKind.UPDATED,
                        "corr-swallow")
        ).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Fake ReasonerService implementation
    // -------------------------------------------------------------------------

    /**
     * In-process implementation of {@code ReasonerService} used as a test double.
     *
     * <p>Captures the incoming {@link ResourceUpdate} and either returns a success
     * response or returns a gRPC {@code INTERNAL} error, depending on
     * {@link #serverShouldFail}.
     */
    private class FakeReasonerService
            extends ReasonerServiceGrpc.ReasonerServiceImplBase {

        @Override
        public void triggerUpdate(ResourceUpdate request,
                                  StreamObserver<TriggerResponse> responseObserver) {
            capturedRequest.set(request);

            if (serverShouldFail) {
                responseObserver.onError(
                        Status.INTERNAL
                                .withDescription("Simulated Palamedes failure")
                                .asRuntimeException());
            } else {
                responseObserver.onNext(
                        TriggerResponse.newBuilder()
                                .setAccepted(true)
                                .setTrackingId("tracking-001")
                                .build());
                responseObserver.onCompleted();
            }
        }
    }
}
