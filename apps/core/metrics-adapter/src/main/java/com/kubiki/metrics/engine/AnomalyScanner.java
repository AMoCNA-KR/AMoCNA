package com.kubiki.metrics.engine;

import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.metrics.graph.GraphWriter;
import com.kubiki.metrics.prometheus.PrometheusClient;
import com.kubiki.metrics.prometheus.QueryThreshold;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AnomalyScanner {
    private final PrometheusClient prometheusClient;
    private final GraphWriter graphWriter;
    private final RabbitTemplate rabbitTemplate;
    private final List<QueryThreshold> thresholds = new ArrayList<>();

    public AnomalyScanner(PrometheusClient prometheusClient,
                          GraphWriter graphWriter,
                          RabbitTemplate rabbitTemplate) {
        this.prometheusClient = prometheusClient;
        this.graphWriter = graphWriter;
        this.rabbitTemplate = rabbitTemplate;
        
        // Initialize some demo thresholds
        QueryThreshold cpuThreshold = new QueryThreshold();
        cpuThreshold.setName("High CPU Usage");
        cpuThreshold.setQuery("sum(rate(container_cpu_usage_seconds_total[1m])) by (pod) > 0.8");
        cpuThreshold.setThreshold(0.8);
        cpuThreshold.setAnomalyTypeIri("http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#CPUSaturatedState");
        thresholds.add(cpuThreshold);
    }

    @Scheduled(fixedDelayString = "${scanner.interval:10000}")
    public void scan() {
        log.debug("Starting anomaly scan...");
        for (QueryThreshold threshold : thresholds) {
            prometheusClient.queryScalar(threshold.getQuery())
                    .subscribe(value -> {
                        if (value > threshold.getThreshold()) {
                            log.warn("Threshold breached: {} (Value: {})", threshold.getName(), value);
                            
                            // For now, use a placeholder resource IRI. 
                            // In a real scenario, we'd extract the pod name from the query result 
                            // and resolve its IRI via Metis gRPC.
                            String targetResourceIri = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt#Pod_placeholder";
                            
                            graphWriter.instantiateAnomaly(targetResourceIri, threshold.getAnomalyTypeIri());
                            
                            // Notify via RabbitMQ
                            GraphUpdateMessage message = new GraphUpdateMessage();
                            message.setResourceIri(targetResourceIri);
                            message.setOntologyType(threshold.getAnomalyTypeIri());
                            message.setChangeKind("STATE_CHANGED");
                            message.setCorrelationId("metrics-" + UUID.randomUUID());
                            
                            rabbitTemplate.convertAndSend("amocna.direct.exchange", "graph.updates", message);
                        }
                    });
        }
    }
}
