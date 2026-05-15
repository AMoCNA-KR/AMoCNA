package com.kubiki.palamedes.condition.impl;

import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.kubiki.palamedes.constants.OntologyConstants.CLASS_STATE_BASED_CONDITION;

@Component
public class SparqlConditionStrategy implements ConditionStrategy {
    private static final Logger log = LoggerFactory.getLogger(SparqlConditionStrategy.class);
    private final GraphDBGateway gateway;
    private final PalamedesProperties properties;

    public SparqlConditionStrategy(GraphDBGateway gateway, PalamedesProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    @Override
    public boolean supports(IRI conditionType) {
        String namespace = properties.ontology().moamNamespace();
        return (namespace + CLASS_STATE_BASED_CONDITION).equals(conditionType.stringValue());
    }

    @Override
    public boolean evaluate(ActionData.Condition condition) {
        log.debug("Evaluating SPARQL condition: {}", condition.id());
        try {
            return gateway.executeConditionQuery(condition.policy());
        } catch (Exception e) {
            log.error("Error evaluating SPARQL condition {}: {}", condition.id(), e.getMessage());
            return false;
        }
    }
}
