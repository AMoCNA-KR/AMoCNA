package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateRepositoryTest {
    @Mock private SparqlClient sparqlClient;
    @Mock private SparqlQueryBuilder queryBuilder;
    @Mock private OntologyRegistry registry;
    @Mock private SparqlQueryBuilder.QueryBuilder builder;

    private StateRepository repository;
    private final IRI actionId = SimpleValueFactory.getInstance().createIRI("http://test/action1");

    @BeforeEach
    void setUp() {
        repository = new StateRepository(sparqlClient, queryBuilder, registry);
    }

    @Test
    void shouldTransitionState() {
        when(queryBuilder.builder()).thenReturn(builder);
        when(builder.template(anyString())).thenReturn(builder);
        when(builder.variable(anyString(), any())).thenReturn(builder);
        when(builder.build()).thenReturn("SPARQL");
        when(sparqlClient.executeUpdateWithSuccess("SPARQL")).thenReturn(true);
        when(registry.moam(anyString())).thenReturn(SimpleValueFactory.getInstance().createIRI("http://test/State"));

        boolean result = repository.transition(actionId, WorkflowState.INITIAL, WorkflowState.PLANNED);

        assertTrue(result);
        verify(sparqlClient).executeUpdateWithSuccess("SPARQL");
    }
}
