package com.kubiki.metrics;

import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableDaedalusRepositories(basePackages = "com.kubiki.metrics.graph")
public class MetricsAdapterApplication {
    static void main(String[] args) {
        SpringApplication.run(MetricsAdapterApplication.class, args);
    }
}
