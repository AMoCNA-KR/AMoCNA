package com.kubiki.metis.notification;

import com.kubiki.palamedes.grpc.ChangeKind;
import com.kubiki.palamedes.grpc.ReasonerServiceGrpc;
import com.kubiki.palamedes.grpc.ResourceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget notifier that informs the Palamedes reasoning module about
 * successful knowledge-base updates via the {@code ReasonerService.TriggerUpdate} RPC.
 *
 * <p>Failures are logged at ERROR level and silently swallowed — a Palamedes
 * outage must never roll back a successful graph update.
 */
@Component
public class PalamedesNotifier {

    private static final Logger log = LoggerFactory.getLogger(PalamedesNotifier.class);

    private final ReasonerServiceGrpc.ReasonerServiceBlockingStub stub;

    public PalamedesNotifier(ReasonerServiceGrpc.ReasonerServiceBlockingStub stub) {
        this.stub = stub;
    }

    /**
     * Notifies Palamedes that a resource has changed in the knowledge base.
     *
     * <p>Any exception thrown by the gRPC call is caught, logged at ERROR level
     * (including the {@code correlationId}), and discarded. The method never
     * rethrows and never retries.
     *
     * @param resourceIri   fully-qualified IRI of the affected resource
     * @param ontologyType  fully-qualified CNEEOnt class IRI of the resource
     * @param changeKind    the kind of change that occurred
     * @param correlationId correlation identifier from the originating {@code SensorBatch}
     */
    public void notify(String resourceIri, String ontologyType,
                       ChangeKind changeKind, String correlationId) {
        ResourceUpdate resourceUpdate = ResourceUpdate.newBuilder()
                .setResourceIri(resourceIri)
                .setOntologyType(ontologyType)
                .setChangeKind(changeKind)
                .setCorrelationId(correlationId)
                .build();

        try {
            var response = stub.triggerUpdate(resourceUpdate);
            log.debug("Palamedes TriggerUpdate succeeded [correlationId={}]: accepted={}, trackingId={}",
                    correlationId, response.getAccepted(), response.getTrackingId());
        } catch (Exception e) {
            log.error("Failed to notify Palamedes [correlationId={}]: {}",
                    correlationId, e.getMessage(), e);
        }
    }
}
