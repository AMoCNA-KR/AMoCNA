package com.kubiki.palamedes.condition;

import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConditionFactory {
    private final List<ConditionStrategy> strategies;
    private final OntologyRegistry registry;

    public ConditionFactory(List<ConditionStrategy> strategies, OntologyRegistry registry) {
        this.strategies = strategies;
        this.registry = registry;
    }

    public Optional<ConditionStrategy> getStrategy(IRI conditionType) {
        return strategies.stream()
                .filter(s -> s.supports(conditionType))
                .findFirst();
    }
}
