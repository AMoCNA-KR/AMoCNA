package com.kubiki.palamedes.templating;

import com.google.common.io.Resources;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.templating.types.IndividualType;
import com.kubiki.palamedes.templating.types.IriType;
import com.kubiki.palamedes.templating.types.TemplatingType;
import org.eclipse.rdf4j.model.IRI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparqlTemplateEngineTest {

    @Mock
    private PalamedesProperties properties;

    private final String queryToTemplate = Resources.toString(Resources.getResource("sparql/query-to-template.sparql"), StandardCharsets.UTF_8);
    private final String expectedQuery = Resources.toString(Resources.getResource("sparql/expected-query.sparql"), StandardCharsets.UTF_8);

    SparqlTemplateEngineTest() throws IOException {}

    @Test
    @DisplayName("Should populate template query with variables and prefixes")
    void shouldPopulateTemplateQueryWithVariablesAndPrefixes() {
        when(properties.ontology()).thenReturn(new PalamedesProperties.Ontology(
                "http://example.com/actions#",
                "acs",
                "http://example.com/resources#",
                "res",
                "http://example.com/bridge#",
                "br",
                null
        ));

        LinkedList<TemplatingType<?>> variables = new LinkedList<>();
        variables.add(new IndividualType("STATE_INITIAL", "State_Initial"));
        variables.add(new IndividualType("STATE_PLANNED", "State_Planned"));
        variables.add(new IndividualType("STATE_VALIDATED", "State_Validated"));
        variables.add(new IndividualType("STATE_INPROGRESS", "State_InProgress"));
        variables.add(new IndividualType("STATE_COMPENSATING", "State_Compensating"));
        IRI action = new IRI() {
            @Override
            public String getNamespace() {
                return "http://example.com/resources#Action_123";
            }

            @Override
            public String getLocalName() {
                return "http://example.com/resources#Action_123";
            }

            @Override
            public String stringValue() {
                return "http://example.com/resources#Action_123";
            }
        };
        variables.add(new IriType("actionId", action));

        SparqlTemplateEngine sparqlTemplateEngine = new SparqlTemplateEngine(properties);
        String populatedQuery = sparqlTemplateEngine.populateTemplate(queryToTemplate, variables);

        assertEquals(expectedQuery, populatedQuery);

    }
}