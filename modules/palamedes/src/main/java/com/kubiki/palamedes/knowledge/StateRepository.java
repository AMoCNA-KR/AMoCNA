package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.stereotype.Repository;

@Repository
public class StateRepository {
    private final SparqlClient sparqlClient;
    private final SparqlQueryBuilder sparqlQueryBuilder;
    private final OntologyRegistry ontologyRegistry;

    public StateRepository(SparqlClient sparqlClient,
                           SparqlQueryBuilder sparqlQueryBuilder,
                           OntologyRegistry ontologyRegistry) {
        this.sparqlClient = sparqlClient;
        this.sparqlQueryBuilder = sparqlQueryBuilder;
        this.ontologyRegistry = ontologyRegistry;
    }

    /**
     * Performs an atomic state transition for an action.
     * @param actionId The IRI of the action.
     * @param from The current state to transition from.
     * @param to The target state to transition to.
     * @return true if the transition succeeded, false if the action was not in the 'from' state.
     */
    public boolean transition(IRI actionId, WorkflowState from, WorkflowState to) {
        String sparql = sparqlQueryBuilder.builder()
                .template("atomic-transition")
                .variable("actionId", actionId)
                .variable("fromState", ontologyRegistry.moam(from.getFragment()))
                .variable("toState", ontologyRegistry.moam(to.getFragment()))
                .build();

        return sparqlClient.executeUpdateWithSuccess(sparql);
    }
}
