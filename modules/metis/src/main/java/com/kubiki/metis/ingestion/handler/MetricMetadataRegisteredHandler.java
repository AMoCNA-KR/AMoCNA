package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.MetricMetadataRegisteredEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.palamedes.grpc.ChangeKind;
import org.springframework.stereotype.Component;

/**
 * Handles {@link SensorEvent.EventCase#METRIC_METADATA_REGISTERED} events by
 * registering metric metadata for the described resource in the knowledge base.
 */
@Component
public class MetricMetadataRegisteredHandler implements SensorEventHandler {

    private final KnowledgeBaseWriter writer;

    public MetricMetadataRegisteredHandler(KnowledgeBaseWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supports(SensorEvent.EventCase eventCase) {
        return SensorEvent.EventCase.METRIC_METADATA_REGISTERED.equals(eventCase);
    }

    @Override
    public HandlerResult handle(SensorEvent event, String correlationId) {
        MetricMetadataRegisteredEvent metricEvent = event.getMetricMetadataRegistered();
        String resourceIri  = metricEvent.getResourceIri();
        String endpointUrl  = metricEvent.getEndpointUrl();

        if (resourceIri == null || resourceIri.isBlank()) {
            return HandlerResult.failure("MetricMetadataRegisteredHandler: resource_iri must not be blank");
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            return HandlerResult.failure("MetricMetadataRegisteredHandler: endpoint_url must not be blank");
        }

        try {
            writer.registerMetricMetadata(metricEvent);
            return HandlerResult.success(resourceIri, "cnee:Metric", ChangeKind.UPDATED);
        } catch (KnowledgeBaseException e) {
            return e.getCause() != null
                    ? HandlerResult.graphDbFailure(e.getMessage())
                    : HandlerResult.failure(e.getMessage());
        }
    }
}
