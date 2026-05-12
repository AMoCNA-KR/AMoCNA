package com.kubiki.metis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "metis")
public record MetisProperties(
        @NestedConfigurationProperty GraphDB graphdb,
        @NestedConfigurationProperty Ontology ontology,
        @NestedConfigurationProperty Palamedes palamedes
) {
    public record GraphDB(String url, String repositoryId, int timeoutMs) {
    }

    public record Ontology(String cneeNamespace) {
    }

    public record Palamedes(String host, int port) {
    }
}
