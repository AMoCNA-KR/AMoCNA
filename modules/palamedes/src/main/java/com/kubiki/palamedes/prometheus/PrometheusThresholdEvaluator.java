package com.kubiki.palamedes.prometheus;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Periodically evaluates Prometheus threshold definitions and writes anomaly
 * states to GraphDB when thresholds are crossed.
 *
 * <p>This is the Prometheus-based analysis component — it replaces the old
 * Drools rule engine from the Metrics-Adapter. Threshold definitions are
 * loaded from configuration (extensible to GUI/GraphDB in the future).
 *
 * <p>Evaluation interval is controlled by {@code palamedes.prometheus.evaluation-interval-ms}
 * (default: 30000ms).
 */
@Service
public class PrometheusThresholdEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PrometheusThresholdEvaluator.class);

    private final PrometheusClient prometheusClient;
    private final GraphDBGateway graphDBGateway;
    private final PalamedesProperties properties;
    private final ThresholdsLoader loader;

    public PrometheusThresholdEvaluator(PrometheusClient prometheusClient,
                                        GraphDBGateway graphDBGateway,
                                        PalamedesProperties properties, ThresholdsLoader loader) {
        this.prometheusClient = prometheusClient;
        this.graphDBGateway = graphDBGateway;
        this.properties = properties;
        this.loader = loader;
        log.info("PrometheusThresholdEvaluator initialized with {} threshold(s)",
                this.loader.getThresholds() != null ? this.loader.getThresholds().size() : 0);
    }

    /**
     * Runs on a fixed schedule. For each configured threshold, queries Prometheus
     * and writes anomaly states to GraphDB for resources that violate the threshold.
     */
    @Scheduled(fixedDelayString = "${palamedes.prometheus.evaluation-interval-ms:30000}")
    public void evaluate() {
        List<ThresholdDefinition> thresholds = loader.getThresholds();
        if (thresholds == null || thresholds.isEmpty()) {
            log.debug("No thresholds configured — skipping evaluation");
            return;
        }

        log.debug("Evaluating {} Prometheus threshold(s)...", thresholds.size());

        for (ThresholdDefinition threshold : thresholds) {
            evaluateThreshold(threshold);
        }
    }

    private void evaluateThreshold(ThresholdDefinition threshold) {
        List<PrometheusClient.QueryResult> results = prometheusClient.query(threshold.query());

        for (PrometheusClient.QueryResult result : results) {
            if (isViolated(result.value(), threshold.operator(), threshold.value())) {
                String resourceName = result.labels().get(threshold.resourceLabel());
                String namespace = threshold.namespaceLabel() != null
                        ? result.labels().get(threshold.namespaceLabel())
                        : null;

                if (StringUtils.isBlank(resourceName)) {
                    log.warn("Threshold '{}' violated but resource label '{}' is missing from result",
                            threshold.name(), threshold.resourceLabel());
                    continue;
                }

                String resourceIri = buildResourceIri(threshold.resourceKind(), namespace, resourceName);

                log.info("Threshold '{}' violated: resource={}, value={}, threshold={} {}",
                        threshold.name(), resourceIri, result.value(), threshold.operator(), threshold.value());

                String anomalyStateIri = properties.ontology().resourcesNamespace() + threshold.anomalyState();
                graphDBGateway.updateResourceState(resourceIri, anomalyStateIri);
            }
        }
    }

    private boolean isViolated(double actual, String operator, double threshold) {
        return switch (operator) {
            case ">"  -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "<"  -> actual < threshold;
            case "<=" -> actual <= threshold;
            case "==" -> actual == threshold;
            default   -> false;
        };
    }

    private String buildResourceIri(String kind, String namespace, String name) {
        String ns = properties.ontology().resourcesNamespace();
        if (namespace != null && !namespace.isBlank()) {
            return ns + kind + "_" + namespace + "_" + name;
        }
        return ns + kind + "_" + name;
    }
}
