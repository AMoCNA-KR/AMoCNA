package com.kubiki.palamedes.config;

import com.kubiki.palamedes.prometheus.ThresholdDefinition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "palamedes")
@Data
public class PalamedesProperties {
    @NestedConfigurationProperty
    private GraphDB graphdb;
    @NestedConfigurationProperty
    private Ontology ontology;
    @NestedConfigurationProperty
    private Prometheus prometheus;
    @NestedConfigurationProperty
    private Engine engine;
    @NestedConfigurationProperty
    private Utilities utilities;
    @NestedConfigurationProperty
    private List<ThresholdDefinition> thresholds = new ArrayList<>();

    public record GraphDB(String url, String repositoryId, int timeoutMs) {}

    public record Ontology(String actionsNamespace, String actionsPrefix, String resourcesNamespace, String resourcesPrefix, String bridgeNamespace, String bridgePrefix, Map<String, String> states) {
        public String getCneeNamespace() {
            return resourcesNamespace();
        }
    }

    public record Prometheus(String url, long evaluationIntervalMs) {}

    public record Engine(long pipelineRateMs, int defaultIdempotencySeconds) {}

    public record Utilities(String actionPrefix, String stepPrefix, String compensationPrefix, int sizeOfGeneratedUuid) {}

    // Compatibility methods for record-style access
    public GraphDB graphdb() { return graphdb; }
    public Ontology ontology() { return ontology; }
    public Prometheus prometheus() { return prometheus; }
    public Engine engine() { return engine; }
    public Utilities utilities() { return utilities; }
    public List<ThresholdDefinition> thresholds() { return thresholds; }
}
