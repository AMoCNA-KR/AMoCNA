package com.kubiki.palamedes.config;

import com.kubiki.palamedes.prometheus.ThresholdDefinition;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "palamedes")
public record PalamedesProperties(
        @NestedConfigurationProperty GraphDB graphdb,
        @NestedConfigurationProperty Ontology ontology,
        @NestedConfigurationProperty Prometheus prometheus,
        @NestedConfigurationProperty Engine engine,
        @NestedConfigurationProperty Utilities utilities
) {
    private final static List<String> AVAILABLE_STATE_KEYS = List.of(
            "initial",
            "planned",
            "validated",
            "in-progress",
            "succeeded",
            "failed",
            "compensating"
    );
  
    public record GraphDB(String url, String repositoryId, int timeoutMs) {
        public GraphDB {
            Objects.requireNonNull(url, "palamedes.graphdb.url must not be null");
            Objects.requireNonNull(repositoryId, "palamedes.graphdb.repositoryId must not be null");
            PropertiesUtils.requiredPositive(timeoutMs, "palamedes.graphdb.timeoutMs");
        }
    }

    public record Ontology(
            String actionsNamespace,
            String actionsPrefix,
            String resourcesNamespace,
            String resourcesPrefix,
            String bridgeNamespace,
            String bridgePrefix,
            Map<String, String> states
    ) {
        public Ontology {
            Objects.requireNonNull(actionsNamespace, "palamedes.ontology.actionsNamespace must not be null");
            Objects.requireNonNull(actionsPrefix, "palamedes.ontology.actionsPrefix must not be null");
            Objects.requireNonNull(resourcesNamespace, "palamedes.ontology.resourcesNamespace must not be null");
            Objects.requireNonNull(resourcesPrefix, "palamedes.ontology.resourcesPrefix must not be null");
            Objects.requireNonNull(bridgeNamespace, "palamedes.ontology.bridgeNamespace must not be null");
            Objects.requireNonNull(bridgePrefix, "palamedes.ontology.bridgePrefix must not be null");
            Objects.requireNonNull(states, "palamedes.ontology.states must not be null");
            PropertiesUtils.availableOptions(states, AVAILABLE_STATE_KEYS);
        }
    }

    public record Prometheus(String url) {
        public Prometheus {
            Objects.requireNonNull(url, "palamedes.prometheus.url must not be null");
        }
    }

    public record Engine(long fallbackPipelineRateMs, long fallbackAnomalyScanRateMs, int defaultIdempotencySeconds, int batchSize) {
        public Engine {
            PropertiesUtils.requiredPositive(fallbackPipelineRateMs, "palamedes.engine.fallbackPipelineRateMs");
            PropertiesUtils.requiredPositive(fallbackAnomalyScanRateMs, "palamedes.engine.fallbackAnomalyScanRateMs");
            PropertiesUtils.requiredPositive(defaultIdempotencySeconds, "palamedes.engine.defaultIdempotencySeconds");
            PropertiesUtils.requiredPositive(batchSize, "palamedes.engine.batchSize");
        }
    }

    public record Utilities(String actionPrefix, String stepPrefix, String compensationPrefix, int sizeOfGeneratedUuid) {
        public Utilities {
            Objects.requireNonNull(actionPrefix, "palamedes.utilities.actionPrefix must not be null");
            Objects.requireNonNull(stepPrefix, "palamedes.utilities.stepPrefix must not be null");
            Objects.requireNonNull(compensationPrefix, "palamedes.utilities.compensationPrefix must not be null");
            PropertiesUtils.requiredPositive(sizeOfGeneratedUuid, "palamedes.utilities.sizeOfGeneratedUuid");
        }
    }
}
