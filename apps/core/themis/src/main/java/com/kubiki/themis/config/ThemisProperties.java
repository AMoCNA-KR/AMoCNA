package com.kubiki.themis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "themis")
public record ThemisProperties(
        @NestedConfigurationProperty Secret secret,
        @NestedConfigurationProperty Execution execution
) {
    public record Secret(String bearerToken) {
    }

    public record Execution(int postConditionDelayMs) {
    }
}
