package com.kubiki.palamedes;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.kubiki.palamedes", "com.kubiki.common"})
@EnableConfigurationProperties({PalamedesProperties.class, AmocnaCommonProperties.class})
public class PalamedesApplication {
    static void main(String[] args) {
        SpringApplication.run(PalamedesApplication.class, args);
    }
}
