package com.kubiki.metis.ingestion;

import com.kubiki.daedalus.knowledge.SparqlClient;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.handler.SensorEventHandler;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches a batch of {@link SensorEvent}s to the appropriate
 * {@link SensorEventHandler} and aggregates the results into a {@link ProcessResult}.
 *
 * <p>Spring auto-collects all {@code @Component}-annotated {@link SensorEventHandler}
 * implementations via constructor injection of {@code List<SensorEventHandler>}.
 */
@Component
public class SensorEventProcessor {

    private final List<SensorEventHandler> handlers;
    private final SparqlClient sparqlClient;
    private final MeterRegistry meterRegistry;

    public SensorEventProcessor(List<SensorEventHandler> handlers, SparqlClient sparqlClient, MeterRegistry meterRegistry) {
        this.handlers = handlers;
        this.sparqlClient = sparqlClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Processes each event in the batch in order.
     *
     * <p>For each event:
     * <ol>
     *   <li>Finds the first handler whose {@code supports(eventCase)} returns {@code true}.</li>
     *   <li>If no handler is found, records a failure message and increments {@code failedCount}.</li>
     *   <li>Otherwise, delegates to the handler and accumulates the result.</li>
     * </ol>
     *
     * @param events        the ordered list of sensor events to process
     * @param correlationId the correlation ID from the enclosing {@code SensorBatch}
     * @return a {@link ProcessResult} summarising counts, failure messages, and the first success
     */
    public ProcessResult processBatch(List<SensorEvent> events, String correlationId) {
        int processedCount = 0;
        int failedCount = 0;
        List<String> failureMessages = new ArrayList<>();
        HandlerResult firstSuccess = null;
        boolean graphDbFailed = false;

        List<String> collectedSparql = new ArrayList<>();
        List<HandlerResult> successResults = new ArrayList<>();

        for (SensorEvent event : events) {
            SensorEventHandler handler = findHandler(event);

            if (handler == null) {
                failedCount++;
                failureMessages.add("No handler found for event type: " + event.getEventCase());
                continue;
            }

            HandlerResult result = handler.handle(event, correlationId);

            if (result.success()) {
                if (result.sparqlUpdate() != null) {
                    collectedSparql.add(result.sparqlUpdate());
                }
                successResults.add(result);
                if (firstSuccess == null) {
                    firstSuccess = result;
                }
            } else {
                failedCount++;
                if (result.failureReason() != null) {
                    failureMessages.add(result.failureReason());
                }
                if (result.graphDbFailed()) {
                    graphDbFailed = true;
                }
            }
        }

        if (!collectedSparql.isEmpty() && !graphDbFailed) {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                sparqlClient.executeWithConnection(conn -> {
                    for (String sparql : collectedSparql) {
                        conn.prepareUpdate(sparql).execute();
                    }
                });
                processedCount = successResults.size();
            } catch (Exception e) {
                graphDbFailed = true;
                failedCount += successResults.size();
                failureMessages.add("Batch SPARQL execution failed: " + e.getMessage());
                firstSuccess = null;
            } finally {
                sample.stop(Timer.builder("metis.knowledge.batch.update").register(meterRegistry));
            }
        } else {
            processedCount = successResults.size();
        }

        return new ProcessResult(processedCount, failedCount, failureMessages, firstSuccess, graphDbFailed);
    }

    /**
     * Returns the first handler that supports the event's case, or {@code null} if none does.
     */
    private SensorEventHandler findHandler(SensorEvent event) {
        SensorEvent.EventCase eventCase = event.getEventCase();
        for (SensorEventHandler handler : handlers) {
            if (handler.supports(eventCase)) {
                return handler;
            }
        }
        return null;
    }
}
