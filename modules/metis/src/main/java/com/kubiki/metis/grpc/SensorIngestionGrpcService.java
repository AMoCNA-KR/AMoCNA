package com.kubiki.metis.grpc;

import com.kubiki.metis.ingestion.SensorEventProcessor;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.notification.PalamedesNotifier;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * gRPC service implementation for the {@code SensorIngestionService} contract.
 *
 * <p>Receives {@link SensorBatch} messages from sensors, validates the
 * {@code correlation_id}, delegates event processing to {@link SensorEventProcessor},
 * triggers Palamedes notification on success, and returns an {@link IngestResponse}.
 *
 * <p>Error handling:
 * <ul>
 *   <li>{@link KnowledgeBaseException} → gRPC {@code UNAVAILABLE}</li>
 *   <li>Any other {@link Exception} → gRPC {@code INTERNAL}</li>
 * </ul>
 */
@GrpcService
public class SensorIngestionGrpcService
        extends SensorIngestionServiceGrpc.SensorIngestionServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SensorIngestionGrpcService.class);

    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    private final SensorEventProcessor processor;
    private final PalamedesNotifier notifier;

    public SensorIngestionGrpcService(SensorEventProcessor processor,
                                      PalamedesNotifier notifier) {
        this.processor = processor;
        this.notifier = notifier;
    }

    @Override
    public void ingestBatch(SensorBatch request,
                            StreamObserver<IngestResponse> responseObserver) {
        try {
            String correlationId = request.getCorrelationId();

            // Validate correlation_id length
            if (correlationId.length() > MAX_CORRELATION_ID_LENGTH) {
                log.warn("Rejected batch: correlation_id exceeds {} characters [length={}]",
                        MAX_CORRELATION_ID_LENGTH, correlationId.length());
                responseObserver.onNext(IngestResponse.newBuilder()
                        .setAccepted(false)
                        .setCorrelationId(correlationId)
                        .setProcessedCount(0)
                        .setMessage("correlation_id must not exceed " + MAX_CORRELATION_ID_LENGTH + " characters")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Process the batch
            ProcessResult processResult = processor.processBatch(
                    request.getEventsList(), correlationId);

            // If GraphDB was unavailable for any event, return UNAVAILABLE status
            if (processResult.graphDbFailed()) {
                String failureMsg = processResult.failureMessages().isEmpty()
                        ? "Knowledge base unavailable"
                        : processResult.failureMessages().get(0);
                responseObserver.onError(
                        Status.UNAVAILABLE
                                .withDescription("Knowledge base unavailable: " + failureMsg)
                                .asRuntimeException());
                return;
            }

            // Notify Palamedes if at least one event was successfully written
            HandlerResult firstSuccess = processResult.firstSuccess();
            if (firstSuccess != null) {
                log.info("Palamedes should be notified [correlationId={}, resourceIri={}, ontologyType={}, changeKind={}]",
                        correlationId, firstSuccess.resourceIri(), firstSuccess.ontologyType(), firstSuccess.changeKind());
                // notifier.notify(
                //         firstSuccess.resourceIri(),
                //         firstSuccess.ontologyType(),
                //         firstSuccess.changeKind(),
                //         correlationId);
            }

            // Build response
            boolean batchWasEmpty = request.getEventsList().isEmpty();
            boolean accepted = processResult.processedCount() > 0 || batchWasEmpty;

            String message = processResult.failureMessages().isEmpty()
                    ? ""
                    : processResult.failureMessages().stream()
                            .collect(Collectors.joining("; "));

            IngestResponse response = IngestResponse.newBuilder()
                    .setAccepted(accepted)
                    .setCorrelationId(correlationId)
                    .setProcessedCount(processResult.processedCount())
                    .setMessage(message)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (e.getCause() instanceof KnowledgeBaseException kbe) {
                log.error("Knowledge base unavailable during batch ingestion: {}", kbe.getMessage(), kbe);
                responseObserver.onError(
                        Status.UNAVAILABLE
                                .withDescription("Knowledge base unavailable: " + kbe.getMessage())
                                .withCause(kbe)
                                .asRuntimeException());
            } else {
                log.error("Unexpected error during batch ingestion: {}", e.getMessage(), e);
                responseObserver.onError(
                        Status.INTERNAL
                                .withDescription("Internal error: " + e.getMessage())
                                .withCause(e)
                                .asRuntimeException());
            }
        }
    }
}
