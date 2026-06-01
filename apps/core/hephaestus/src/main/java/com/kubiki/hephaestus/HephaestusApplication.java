package com.kubiki.hephaestus;

import com.kubiki.common.config.AmocnaCommonProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Main entry point for the Hephaestus telemetry & visualizer portal.
 */
@SpringBootApplication(scanBasePackages = {"com.kubiki.hephaestus", "com.kubiki.common"})
@EnableConfigurationProperties(AmocnaCommonProperties.class)
public class HephaestusApplication {
    public static void main(String[] args) {
        SpringApplication.run(HephaestusApplication.class, args);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
