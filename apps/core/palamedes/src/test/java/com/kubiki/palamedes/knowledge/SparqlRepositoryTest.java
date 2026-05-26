package com.kubiki.palamedes.knowledge;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.palamedes.config.BeanConfig;
import com.kubiki.palamedes.config.DaedalusInitializer;
import com.kubiki.palamedes.config.GraphDBConfig;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {BeanConfig.class, DaedalusInitializer.class, GraphDBConfig.class})
@EnableConfigurationProperties({PalamedesProperties.class, AmocnaCommonProperties.class})
@ActiveProfiles("test")
class SparqlRepositoryTest {

    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @MockitoBean
    private Repository realRepository;

    @Autowired
    private SparqlRepository sparqlRepository;

    @Autowired
    private GlobalTemplateContext globalContext;

    @BeforeEach
    void setUp() throws Exception {
        if (inMemoryRepo == null) {
            inMemoryRepo = new SailRepository(new MemoryStore());
            inMemoryRepo.init();
        }
        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.clear();
            conn.commit();
        }
        when(realRepository.getConnection()).thenAnswer(inv -> inMemoryRepo.getConnection());
        when(realRepository.getValueFactory()).thenReturn(vf);
    }

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
