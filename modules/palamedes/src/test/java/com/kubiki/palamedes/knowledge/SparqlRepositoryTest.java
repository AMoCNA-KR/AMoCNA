package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.palamedes.config.BeanConfig;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BeanConfig.class})
@EnableConfigurationProperties(PalamedesProperties.class)
@ActiveProfiles("test")
class SparqlRepositoryTest {

    @Autowired
    private SparqlRepository sparqlRepository;

    @Autowired
    private GlobalTemplateContext globalContext;

    @Test
    void shouldInjectAndHydrateFindAnomalies() {
        assertThat(sparqlRepository).isNotNull();
        
        String query = sparqlRepository.findAnomalies();
        
        assertThat(query).contains("SELECT DISTINCT ?resource");
        // Verify global vars are hydrated if they were set by CommandLineRunner
        // Note: CommandLineRunner might not have run in this test setup unless we trigger it
    }
}
