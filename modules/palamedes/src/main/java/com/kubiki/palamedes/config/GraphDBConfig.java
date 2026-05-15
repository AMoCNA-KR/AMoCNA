package com.kubiki.palamedes.config;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphDBConfig {

    private final PalamedesProperties properties;

    public GraphDBConfig(PalamedesProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Repository repository() {
        HTTPRepository repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
        repository.init();
        return repository;
    }
}
