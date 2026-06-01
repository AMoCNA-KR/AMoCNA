package com.kubiki.metrics.engine;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.metrics.graph.GraphWriter;
import com.kubiki.metrics.prometheus.PrometheusClient;
import com.kubiki.metrics.prometheus.ThresholdsLoader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyScanner {
    private final PrometheusClient prometheusClient;
    private final GraphWriter graphWriter;
    private final RabbitTemplate rabbitTemplate;
    private final ThresholdsLoader thresholdsLoader;
    private final AmocnaCommonProperties properties;

    private final MeterRegistry meterRegistry;

    private final Map<String, Integer> violationCounter = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${scanner.interval:10000}")
    public void scan() {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Starting anomaly scan...");

        List<AnomalyBatchItem> batchItems = new ArrayList<>();

        Flux.fromIterable(thresholdsLoader.getThresholds())
                .flatMap(threshold -> prometheusClient.query(threshold.query())
                        .onErrorResume(e -> {
                            log.error("Failed to query Prometheus for threshold: {}", threshold.name(), e);
                            return Flux.empty();
                        })
                        .flatMap(result -> {
                            String resourceName = result.labels().get(threshold.resourceLabel());
                            String namespace = threshold.namespaceLabel() != null
                                    ? result.labels().get(threshold.namespaceLabel())
                                    : null;

                            if (!StringUtils.hasText(resourceName)) {
                                log.warn("Result returned but resource label '{}' is missing", threshold.resourceLabel());
                                return Mono.empty();
                            }

                            String targetResourceIri = buildResourceIri(threshold.resourceKind(), namespace, resourceName);
                            String key = threshold.name() + ":" + targetResourceIri;

                            if (isViolated(result.value(), threshold.operator(), threshold.value())) {
                                int count = violationCounter.getOrDefault(key, 0) + 1;
                                log.warn("[BENCHMARK] Threshold breached at {} for {}: {} for resource {} (Value: {} {} {}). Persistence: {}/{}", System.currentTimeMillis(),
                                        threshold.name(), targetResourceIri, result.value(), threshold.operator(), threshold.value(), count, threshold.persistenceWindow());

                                if (count >= threshold.persistenceWindow()) {
                                    violationCounter.remove(key);
                                    batchItems.add(new AnomalyBatchItem(targetResourceIri, threshold.anomalyState(), true));
                                } else {
                                    violationCounter.put(key, count);
                                }
                            } else {
                                if (violationCounter.containsKey(key)) {
                                    violationCounter.remove(key);
                                }
                                batchItems.add(new AnomalyBatchItem(targetResourceIri, null, false));
                            }
                            return Mono.empty();
                        }))
                .collectList()
                .doOnError(e -> log.error("Unexpected error during anomaly scan", e))
                .onErrorResume(e -> Mono.empty())
                .block();

        if (!batchItems.isEmpty()) {
            executeBatch(batchItems);
        }

        sample.stop(Timer.builder("amocna.monitor.scan.duration").register(meterRegistry));
    }

    private void executeBatch(List<AnomalyBatchItem> items) {
        log.debug("Executing anomaly batch with {} items", items.size());
        
        // Group by resource to avoid redundant operations in the same batch
        Map<String, AnomalyBatchItem> lastActionPerResource = new HashMap<>();
        for (var item : items) {
            lastActionPerResource.put(item.resourceIri(), item);
        }

        for (var item : lastActionPerResource.values()) {
            if (item.isTrigger()) {
                triggerAnomaly(item.resourceIri(), item.anomalyState())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            } else {
                clearAnomaly(item.resourceIri())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }
    }

    private record AnomalyBatchItem(String resourceIri, String anomalyState, boolean isTrigger) {}

    private Mono<Void> triggerAnomaly(String targetResourceIri, String anomalyState) {
        if (properties.ontology() == null || properties.ontology().resourcesNamespace() == null || anomalyState == null) {
            log.error("Cannot trigger anomaly: ontology properties or state is null");
            return Mono.empty();
        }
        String anomalyStateIri = properties.ontology().resourcesNamespace() + anomalyState;
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
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> clearAnomaly(String targetResourceIri) {
        if (properties.ontology() == null || properties.ontology().resourcesNamespace() == null) {
            log.error("Cannot clear anomaly: ontology properties are null");
            return Mono.empty();
        }
        String baseStateIri = properties.ontology().resourcesNamespace() + "State";
        return Mono.fromRunnable(() -> {
            graphWriter.clearAnomalies(targetResourceIri);

            // Notify via RabbitMQ
            GraphUpdateMessage message = new GraphUpdateMessage(
                    targetResourceIri,
                    baseStateIri,
                    "DELETED",
                    "metrics-" + UUID.randomUUID()
            );

            rabbitTemplate.convertAndSend("amocna.direct.exchange", "graph.updates", message);
        }).subscribeOn(Schedulers.boundedElastic()).then();
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
            return properties.ontology().resourcesNamespace() + kind + "_" + namespace + "_" + name;
        }
        return properties.ontology().resourcesNamespace() + kind + "_" + name;
    }
}
