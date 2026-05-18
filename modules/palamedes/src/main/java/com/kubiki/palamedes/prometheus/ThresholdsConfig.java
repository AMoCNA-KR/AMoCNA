package com.kubiki.palamedes.prometheus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code palamedes.thresholds} list from application.yml.
 *
 * <p>Each entry defines a Prometheus query + threshold that, when crossed,
 * triggers an anomaly state in the knowledge graph.
 *
 * <p>In the future, this can be replaced or supplemented by a GUI that
 * writes threshold definitions to a database or GraphDB.
 */
@ConfigurationProperties(prefix = "palamedes")
public record ThresholdsConfig(
        List<ThresholdDefinition> thresholds
) {
    public ThresholdsConfig {
        if (thresholds == null) thresholds = List.of();
    }
}
