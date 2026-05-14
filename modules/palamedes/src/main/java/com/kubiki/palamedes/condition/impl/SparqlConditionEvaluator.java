package com.kubiki.palamedes.condition.impl;

import com.kubiki.palamedes.condition.ConditionEvaluator;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.constants.OntologyConstants;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Component;

import static com.kubiki.palamedes.constants.OntologyConstants.*;

@Component
public class SparqlConditionEvaluator implements ConditionEvaluator {
    private final GraphDBGateway graphDBGateway;
    private final PalamedesProperties properties;

    public SparqlConditionEvaluator(GraphDBGateway graphDBGateway, PalamedesProperties properties) {
        this.graphDBGateway = graphDBGateway;
        this.properties = properties;
    }

    @Override
    public boolean supports(IRI conditionType) {
        String stateBasedCondition = properties.ontology().moamNamespace() + CLASS_STATE_BASED_CONDITION;
        return stateBasedCondition.equals(conditionType.stringValue());
    }

    @Override
    public boolean evaluate(ActionData.ConditionData condition) {
        return graphDBGateway.executeConditionQuery(condition.policy());
    }
}
