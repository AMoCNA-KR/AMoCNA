package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StateRepository {
    private final SparqlClient sparqlClient;
    private final SparqlRepository sparqlRepository;
    private final com.kubiki.common.ontology.OntologyRegistry ontologyRegistry;
    private final WorkflowStateMapper mapper;


    public boolean transition(IRI actionId, WorkflowState from, WorkflowState to) {
        String sparql = sparqlRepository.atomicTransition(
                actionId.stringValue(),
                ontologyRegistry.actionsOntology(mapper.getFragment(from)).stringValue(),
                ontologyRegistry.actionsOntology(mapper.getFragment(to)).stringValue()
        );

        return sparqlClient.executeUpdateWithSuccess(sparql);
    }
}
