package com.kubiki.palamedes.knowledge;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.palamedes.config.BeanConfig;
import com.kubiki.palamedes.config.DaedalusInitializer;
import com.kubiki.palamedes.config.GraphDBConfig;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.eclipse.rdf4j.query.BindingSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BeanConfig.class, DaedalusInitializer.class, GraphDBConfig.class})
@EnableConfigurationProperties({PalamedesProperties.class, AmocnaCommonProperties.class})
@ActiveProfiles("test")
class SparqlRepositoryTest {

    @Autowired
    private SparqlRepository sparqlRepository;

    @Autowired
    private GlobalTemplateContext globalContext;

    @Test
    void shouldInjectAndHydrateFindAnomalies() {
        assertThat(sparqlRepository).isNotNull();

        List<BindingSet> result = sparqlRepository.findAnomalies();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldHydrateParameterizedQuery() {
        String actionId = "http://example.org/action/123";
        List<BindingSet> result = sparqlRepository.findDependents(actionId);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldHydrateBulkActionStructureQuery() {
        String action1 = "http://example.org/action/1";
        String action2 = "http://example.org/action/2";
        String actions = "<" + action1 + "> <" + action2 + ">";

        List<BindingSet> result = sparqlRepository.fetchActionStructures(actions);

        assertThat(result).isNotNull();
    }
}
