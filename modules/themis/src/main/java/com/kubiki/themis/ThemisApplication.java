package com.kubiki.themis;

import com.kubiki.themis.config.ThemisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@EnableConfigurationProperties(ThemisProperties.class)
public class ThemisApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThemisApplication.class, args);
    }
}
