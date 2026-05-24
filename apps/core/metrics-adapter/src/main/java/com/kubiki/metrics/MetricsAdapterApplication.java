package com.kubiki.metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetricsAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetricsAdapterApplication.class, args);
    }
}
