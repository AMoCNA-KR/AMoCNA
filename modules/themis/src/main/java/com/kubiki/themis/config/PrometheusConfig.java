package com.kubiki.themis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PrometheusConfig {

    @Bean(name = "prometheusRestClient")
    @ConditionalOnProperty(prefix = "themis.prometheus", name = "url")
    public RestClient prometheusRestClient(ThemisProperties properties, RestClient.Builder builder) {
        return builder.baseUrl(properties.prometheus().url()).build();
    }
}
