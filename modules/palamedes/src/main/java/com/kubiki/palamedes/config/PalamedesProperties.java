package com.kubiki.palamedes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Root configuration class for Palamedes, following the "Configuration Properties at Scale" pattern.
 * Uses nested records for namespace-based discovery.
 */
@ConfigurationProperties(prefix = "palamedes")
public record PalamedesProperties(
        @NestedConfigurationProperty GraphDB graphdb,
        @NestedConfigurationProperty Ontology ontology,
        @NestedConfigurationProperty Prometheus prometheus
) {
    public record GraphDB(String url, String repositoryId, int timeoutMs) {
    }

    public record Ontology(String moamNamespace, String cneeNamespace, String bridgeNamespace) {
    }

    public record Prometheus(String url) {
    }
}
