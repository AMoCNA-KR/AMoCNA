package com.kubiki.hephaestus.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data transfer object representing a single Prometheus threshold definition rule.
 * Matches the metrics-adapter's ThresholdDefinition structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThresholdDto(
        String name,
        String query,
        String operator,
        double value,
        String anomalyState,
        String resourceKind,
        String resourceLabel,
        String namespaceLabel,
        Integer persistenceWindow
) {
    public ThresholdDto {
        if (persistenceWindow == null) {
            persistenceWindow = 0;
        }
    }
}
