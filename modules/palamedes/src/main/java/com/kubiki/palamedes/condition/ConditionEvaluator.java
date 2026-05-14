package com.kubiki.palamedes.condition;

import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;

public interface ConditionEvaluator {
    boolean supports(IRI conditionType);
    boolean evaluate(ActionData.ConditionData condition);
}
