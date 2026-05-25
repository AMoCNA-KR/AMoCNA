package com.kubiki.palamedes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "palamedes")
public record PalamedesProperties(
        @NestedConfigurationProperty Engine engine,
        @NestedConfigurationProperty States states,
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



    public record States(Map<String, String> actionStates) {
        public States {
            Objects.requireNonNull(actionStates, "palamedes.states.actionStates must not be null");
            com.kubiki.common.config.PropertiesUtils.availableOptions(actionStates, AVAILABLE_STATE_KEYS);
        }
    }

    public record Engine(long fallbackPipelineRateMs, long fallbackAnomalyScanRateMs, int defaultIdempotencySeconds,
                         int batchSize) {
        public Engine {
            com.kubiki.common.config.PropertiesUtils.requiredPositive(fallbackPipelineRateMs, "palamedes.engine.fallbackPipelineRateMs");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(fallbackAnomalyScanRateMs, "palamedes.engine.fallbackAnomalyScanRateMs");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(defaultIdempotencySeconds, "palamedes.engine.defaultIdempotencySeconds");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(batchSize, "palamedes.engine.batchSize");
        }
    }

    public record Utilities(String actionPrefix, String stepPrefix, String compensationPrefix,
                            int sizeOfGeneratedUuid) {
        public Utilities {
            Objects.requireNonNull(actionPrefix, "palamedes.utilities.actionPrefix must not be null");
            Objects.requireNonNull(stepPrefix, "palamedes.utilities.stepPrefix must not be null");
            Objects.requireNonNull(compensationPrefix, "palamedes.utilities.compensationPrefix must not be null");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(sizeOfGeneratedUuid, "palamedes.utilities.sizeOfGeneratedUuid");
        }
    }
}
