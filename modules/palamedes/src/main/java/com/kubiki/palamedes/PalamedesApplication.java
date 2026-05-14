package com.kubiki.palamedes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PalamedesApplication {
    public static void main(String[] args) {
        SpringApplication.run(PalamedesApplication.class, args);
    }
}
