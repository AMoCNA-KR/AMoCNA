package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.templating.types.IriType;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StateRepository {
    private final SparqlClient sparqlClient;
    private final SparqlQueryBuilder sparqlQueryBuilder;
    private final OntologyRegistry ontologyRegistry;
    private final WorkflowStateMapper mapper;


    public boolean transition(IRI actionId, WorkflowState from, WorkflowState to) {
        String sparql = sparqlQueryBuilder.builder()
                .template("atomic-transition")
                .variable(new IriType("actionId", actionId))
                .variable(new IriType("fromState", ontologyRegistry.actionsOntology(mapper.getFragment(from))))
                .variable(new IriType("toState", ontologyRegistry.actionsOntology(mapper.getFragment(to))))
                .build();

        return sparqlClient.executeUpdateWithSuccess(sparql);
    }
}
