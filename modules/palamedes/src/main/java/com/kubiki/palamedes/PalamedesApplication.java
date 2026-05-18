package com.kubiki.palamedes;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.prometheus.ThresholdsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PalamedesProperties.class, ThresholdsConfig.class})
public class PalamedesApplication {
    public static void main(String[] args) {
        SpringApplication.run(PalamedesApplication.class, args);
    }
}
