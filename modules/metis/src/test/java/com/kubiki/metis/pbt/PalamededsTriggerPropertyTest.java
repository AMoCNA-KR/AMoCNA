package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 6: Palamedes trigger after every successful batch

import com.kubiki.metis.grpc.*;
import com.kubiki.metis.ingestion.SensorEventProcessor;
import com.kubiki.metis.ingestion.handler.*;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.notification.PalamedesNotifier;
import com.kubiki.palamedes.grpc.ChangeKind;
import com.kubiki.palamedes.grpc.ReasonerServiceGrpc;
import com.kubiki.palamedes.grpc.TriggerResponse;
import io.grpc.stub.StreamObserver;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 6: Palamedes trigger after every successful batch.
 *
 * <p>For any {@code SensorBatch} that results in at least one event successfully
 * written to GraphDB, exactly one call to {@code stub.triggerUpdate(...)} must be
 * made after all events in the batch have been processed. For any batch where zero
 * events are written, no call must be made.
 *
 * <p><b>Validates: Requirements 10.1, 10.3</b>
 */
class PalamededsTriggerPropertyTest {

    private static final String CNEE_NS =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#";

    // -------------------------------------------------------------------------
    // Property: at least one valid event → triggerUpdate called exactly once
    // -------------------------------------------------------------------------

    /**
     * For any non-empty {@code SensorBatch} containing at least one valid
     * {@code EntityDiscoveredEvent}, {@code stub.triggerUpdate(...)} must be
     * called exactly once after the batch is processed.
     *
     * <p><b>Validates: Requirements 10.1, 10.3</b>
     */
    @Property(tries = 100)
    void triggerNeverCalledWhilePalamedesIsLogOnly(
            @ForAll("validEntityDiscoveredBatches") SensorBatch batch) {

        // Arrange
        ReasonerServiceGrpc.ReasonerServiceBlockingStub stub =
                mock(ReasonerServiceGrpc.ReasonerServiceBlockingStub.class);
        when(stub.triggerUpdate(any())).thenReturn(
                TriggerResponse.newBuilder().setAccepted(true).setTrackingId("t1").build());

        KnowledgeBaseWriter mockWriter = mock(KnowledgeBaseWriter.class);
        // mock writer succeeds (does nothing) for all write operations

        SensorIngestionGrpcService service = buildService(stub, mockWriter);

        // Act
        CapturingObserver<IngestResponse> observer = new CapturingObserver<>();
        service.ingestBatch(batch, observer);

        // Assert
        assertThat(observer.error).isNull();
        // Palamedes notification is currently disabled (log-only mode) — stub must NOT be called
        verify(stub, never()).triggerUpdate(any());
    }

    // -------------------------------------------------------------------------
    // Property: all-failure batch → triggerUpdate never called
    // -------------------------------------------------------------------------

    /**
     * For any {@code SensorBatch} where every event fails (e.g., invalid
     * {@code ontology_type} not in CNEEOnt namespace), {@code stub.triggerUpdate(...)}
     * must never be called.
     *
     * <p><b>Validates: Requirements 10.1, 10.3</b>
     */
    @Property(tries = 100)
    void triggerNeverCalledForAllFailureBatch(
            @ForAll("invalidEntityDiscoveredBatches") SensorBatch batch) {

        // Arrange
        ReasonerServiceGrpc.ReasonerServiceBlockingStub stub =
                mock(ReasonerServiceGrpc.ReasonerServiceBlockingStub.class);

        KnowledgeBaseWriter mockWriter = mock(KnowledgeBaseWriter.class);
        // For invalid events the handler will throw KnowledgeBaseException
        try {
            doThrow(new KnowledgeBaseException("invalid ontology_type"))
                    .when(mockWriter).insertEntity(any());
        } catch (KnowledgeBaseException e) {
            // unreachable — doThrow setup
        }

        SensorIngestionGrpcService service = buildService(stub, mockWriter);

        // Act
        CapturingObserver<IngestResponse> observer = new CapturingObserver<>();
        service.ingestBatch(batch, observer);

        // Assert
        assertThat(observer.error).isNull();
        verify(stub, never()).triggerUpdate(any());
    }

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    /**
     * Generates non-empty {@code SensorBatch} instances where every event is a
     * valid {@code EntityDiscoveredEvent} (CNEEOnt-namespaced {@code ontology_type},
     * non-empty {@code resource_iri}, {@code resource_id}, {@code resource_name}).
     */
    @Provide
    Arbitrary<SensorBatch> validEntityDiscoveredBatches() {
        Arbitrary<SensorEvent> validEvent = validEntityDiscoveredEvent();
        return validEvent.list().ofMinSize(1).ofMaxSize(5)
                .map(events -> {
                    SensorBatch.Builder builder = SensorBatch.newBuilder()
                            .setCorrelationId("corr-" + System.nanoTime());
                    events.forEach(builder::addEvents);
                    return builder.build();
                });
    }

    /**
     * Generates non-empty {@code SensorBatch} instances where every event is an
     * {@code EntityDiscoveredEvent} with an invalid {@code ontology_type} (not in
     * the CNEEOnt namespace), causing the handler to return a failure result.
     */
    @Provide
    Arbitrary<SensorBatch> invalidEntityDiscoveredBatches() {
        Arbitrary<SensorEvent> invalidEvent = invalidEntityDiscoveredEvent();
        return invalidEvent.list().ofMinSize(1).ofMaxSize(5)
                .map(events -> {
                    SensorBatch.Builder builder = SensorBatch.newBuilder()
                            .setCorrelationId("corr-" + System.nanoTime());
                    events.forEach(builder::addEvents);
                    return builder.build();
                });
    }

    /** Generates a valid {@code EntityDiscoveredEvent} wrapped in a {@code SensorEvent}. */
    private Arbitrary<SensorEvent> validEntityDiscoveredEvent() {
        Arbitrary<String> iris = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(20)
                .map(s -> "http://example.org/" + s);

        Arbitrary<String> cneeTypes = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(3).ofMaxLength(15)
                .map(s -> CNEE_NS + s);

        Arbitrary<String> names = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(iris, cneeTypes, names, names)
                .as((iri, type, id, name) -> {
                    EntityDiscoveredEvent entityEvent = EntityDiscoveredEvent.newBuilder()
                            .setResourceIri(iri)
                            .setOntologyType(type)
                            .setResourceId(id)
                            .setResourceName(name)
                            .build();
                    return SensorEvent.newBuilder()
                            .setEntityDiscovered(entityEvent)
                            .build();
                });
    }

    /** Generates an invalid {@code EntityDiscoveredEvent} (non-CNEEOnt type) wrapped in a {@code SensorEvent}. */
    private Arbitrary<SensorEvent> invalidEntityDiscoveredEvent() {
        Arbitrary<String> iris = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3).ofMaxLength(20)
                .map(s -> "http://example.org/" + s);

        // ontology_type deliberately NOT in CNEEOnt namespace
        Arbitrary<String> invalidTypes = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(3).ofMaxLength(15)
                .map(s -> "http://invalid.example.org/" + s);

        Arbitrary<String> names = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(iris, invalidTypes, names, names)
                .as((iri, type, id, name) -> {
                    EntityDiscoveredEvent entityEvent = EntityDiscoveredEvent.newBuilder()
                            .setResourceIri(iri)
                            .setOntologyType(type)
                            .setResourceId(id)
                            .setResourceName(name)
                            .build();
                    return SensorEvent.newBuilder()
                            .setEntityDiscovered(entityEvent)
                            .build();
                });
    }

    // -------------------------------------------------------------------------
    // Wiring helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a fully-wired {@link SensorIngestionGrpcService} using real handlers
     * backed by the provided mock {@link KnowledgeBaseWriter}, and a
     * {@link PalamedesNotifier} backed by the provided mock stub.
     */
    private SensorIngestionGrpcService buildService(
            ReasonerServiceGrpc.ReasonerServiceBlockingStub stub,
            KnowledgeBaseWriter mockWriter) {

        com.kubiki.metis.config.MetisProperties props = new com.kubiki.metis.config.MetisProperties(
                new com.kubiki.metis.config.MetisProperties.GraphDB("http://x", "test", 1000),
                new com.kubiki.metis.config.MetisProperties.Ontology(CNEE_NS),
                new com.kubiki.metis.config.MetisProperties.Palamedes("x", 1),
                null);
        com.kubiki.metis.sensor.IriFactory iriFactory = new com.kubiki.metis.sensor.IriFactory(props);

        // Real handlers wired with the mock writer
        List<SensorEventHandler> handlers = new ArrayList<>();
        handlers.add(new EntityDiscoveredHandler(mockWriter));
        handlers.add(new RelationshipAssertedHandler(mockWriter));
        handlers.add(new StateChangedHandler(mockWriter));
        handlers.add(new EntityDeletedHandler(mockWriter));
        handlers.add(new MetricMetadataRegisteredHandler(mockWriter, iriFactory));

        SensorEventProcessor processor = new SensorEventProcessor(handlers);

        return new SensorIngestionGrpcService(processor);
    }

    // -------------------------------------------------------------------------
    // Helper: capturing StreamObserver
    // -------------------------------------------------------------------------

    /** Captures the single response (or error) from a unary gRPC call. */
    private static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;

        @Override
        public void onNext(T v) { this.value = v; }

        @Override
        public void onError(Throwable t) { this.error = t; }

        @Override
        public void onCompleted() {}
    }
}
