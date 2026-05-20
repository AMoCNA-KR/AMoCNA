package com.kubiki.metis;

import com.kubiki.metis.config.MetisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.kubiki.metis", "com.kubiki.common"})
@EnableConfigurationProperties(MetisProperties.class)
public class MetisApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetisApplication.class, args);
    }
}
