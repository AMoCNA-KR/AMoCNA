package com.kubiki.palamedes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableDaedalusRepositories(basePackages = "com.kubiki.palamedes.knowledge")
public class BeanConfig {

    @Bean
    @Primary
    public ObjectMapper getObjectMapper() {
        return new ObjectMapper();
    }
}
