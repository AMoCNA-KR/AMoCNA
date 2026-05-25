package com.kubiki.metrics.engine;

import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.metrics.graph.GraphWriter;
import com.kubiki.metrics.prometheus.PrometheusClient;
import com.kubiki.metrics.prometheus.ThresholdsLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Slf4j
@Service
public class AnomalyScanner {
    private final PrometheusClient prometheusClient;
    private final GraphWriter graphWriter;
    private final RabbitTemplate rabbitTemplate;
    private final ThresholdsLoader thresholdsLoader;

    @Value("${ontology.resources-namespace:http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/}")
    private String resourcesNamespace;

    public AnomalyScanner(PrometheusClient prometheusClient,
                          GraphWriter graphWriter,
                          RabbitTemplate rabbitTemplate,
                          ThresholdsLoader thresholdsLoader) {
        this.prometheusClient = prometheusClient;
        this.graphWriter = graphWriter;
        this.rabbitTemplate = rabbitTemplate;
        this.thresholdsLoader = thresholdsLoader;
    }

    @Scheduled(fixedDelayString = "${scanner.interval:10000}")
    public void scan() {
        log.debug("Starting anomaly scan...");

        Flux.fromIterable(thresholdsLoader.getThresholds())
                .flatMap(threshold -> prometheusClient.query(threshold.query())
                        .filter(result -> isViolated(result.value(), threshold.operator(), threshold.value()))
                        .publishOn(Schedulers.boundedElastic())
                        .flatMap(result -> {
                            String resourceName = result.labels().get(threshold.resourceLabel());
                            String namespace = threshold.namespaceLabel() != null
                                    ? result.labels().get(threshold.namespaceLabel())
                                    : null;

                            if (!StringUtils.hasText(resourceName)) {
                                log.warn("Threshold '{}' violated but resource label '{}' is missing from result",
                                        threshold.name(), threshold.resourceLabel());
                                return Mono.empty();
                            }

                            String targetResourceIri = buildResourceIri(threshold.resourceKind(), namespace, resourceName);
                            String anomalyStateIri = resourcesNamespace + threshold.anomalyState();

                            log.warn("Threshold breached: {} for resource {} (Value: {} {} {})",
                                    threshold.name(), targetResourceIri, result.value(), threshold.operator(), threshold.value());

                            return Mono.fromRunnable(() -> {
                                graphWriter.instantiateAnomaly(targetResourceIri, anomalyStateIri);

                                // Notify via RabbitMQ
                                GraphUpdateMessage message = new GraphUpdateMessage(
                                        targetResourceIri,
                                        anomalyStateIri,
                                        "STATE_CHANGED",
                                        "metrics-" + UUID.randomUUID()
                                );

                                rabbitTemplate.convertAndSend("amocna.direct.exchange", "graph.updates", message);
                            }).subscribeOn(Schedulers.boundedElastic());
                        }))
                .blockLast();
    }

    private boolean isViolated(double actual, String operator, double threshold) {
        return switch (operator) {
            case ">" -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "<" -> actual < threshold;
            case "<=" -> actual <= threshold;
            case "==" -> actual == threshold;
            default -> false;
        };
    }

    private String buildResourceIri(String kind, String namespace, String name) {
        if (StringUtils.hasText(namespace)) {
            return resourcesNamespace + kind + "_" + namespace + "_" + name;
        }
        return resourcesNamespace + kind + "_" + name;
    }
}
