package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.templating.types.IriType;
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

    public boolean transition(IRI actionId, WorkflowState from, WorkflowState to) {
        String sparql = sparqlQueryBuilder.builder()
                .template("atomic-transition")
                .variable(new IriType("actionId", actionId))
                .variable(new IriType("fromState", ontologyRegistry.actionsOntology(from.getFragment())))
                .variable(new IriType("toState", ontologyRegistry.actionsOntology(to.getFragment())))
                .build();

        return sparqlClient.executeUpdateWithSuccess(sparql);
    }
}
