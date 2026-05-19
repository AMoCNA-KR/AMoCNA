package com.kubiki.palamedes.config;

import com.kubiki.palamedes.prometheus.ThresholdDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "palamedes")
public record PalamedesProperties(
        @NestedConfigurationProperty GraphDB graphdb,
        @NestedConfigurationProperty Ontology ontology,
        @NestedConfigurationProperty Prometheus prometheus,
        @NestedConfigurationProperty Engine engine,
        @NestedConfigurationProperty Utilities utilities,
        @NestedConfigurationProperty List<ThresholdDefinition> thresholds
) {
    public record GraphDB(String url, String repositoryId, int timeoutMs) {}

    public record Ontology(String actionsNamespace, String actionsPrefix, String resourcesNamespace, String resourcesPrefix, String bridgeNamespace, String bridgePrefix, Map<String, String> states) {}

    public record Prometheus(String url, long evaluationIntervalMs) {}

    public record Engine(long pipelineRateMs, int defaultIdempotencySeconds) {}

    public record Utilities(String actionPrefix, String stepPrefix, String compensationPrefix, int sizeOfGeneratedUuid) {}
}
