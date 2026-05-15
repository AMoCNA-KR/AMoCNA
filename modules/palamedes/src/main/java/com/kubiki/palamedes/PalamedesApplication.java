package com.kubiki.palamedes;

import com.kubiki.palamedes.config.PalamedesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PalamedesProperties.class)
public class PalamedesApplication {
    public static void main(String[] args) {
        SpringApplication.run(PalamedesApplication.class, args);
    }
}
