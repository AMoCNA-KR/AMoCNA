package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.constants.OntologyConstants;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ExecutionStatus;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphDBGateway {
    private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);

    private static final String TEMPLATE_FETCH_ACTION_STRUCTURE = "fetch-action-structure";
    private static final String TEMPLATE_FIND_ACTIONS_FOR_RESOURCE = "find-actions-for-resource";
    private static final String VAR_ACTION = "action";
    private static final String VAR_ACTION_IRI = "actionIri";
    private static final String VAR_RESOURCE_IRI = "resourceIri";

    private final SparqlClient sparqlClient;
    private final SparqlQueryBuilder sparqlQueryBuilder;
    private final ModelMapper modelMapper;
    private final OntologyRegistry ontologyRegistry;

    public GraphDBGateway(SparqlClient sparqlClient,
                          SparqlQueryBuilder sparqlQueryBuilder,
                          ModelMapper modelMapper,
                          OntologyRegistry ontologyRegistry) {
        this.sparqlClient = sparqlClient;
        this.sparqlQueryBuilder = sparqlQueryBuilder;
        this.modelMapper = modelMapper;
        this.ontologyRegistry = ontologyRegistry;
    }

    /**
     * Updates the current state of an autonomic action in the GraphDB.
     * Uses the Petri Net abstraction individuals (e.g., State_Initial, State_InProgress).
     */
    public void transitionState(IRI actionId, String stateFragment) {
        IRI hasCurrentState = ontologyRegistry.moam("hasCurrentState");
        IRI newState = ontologyRegistry.moam(stateFragment);
        IRI hasLastTransitionTimestamp = ontologyRegistry.moam("hasLastTransitionTimestamp");

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            // Remove old state and timestamp
            conn.remove(actionId, hasCurrentState, null);
            conn.remove(actionId, hasLastTransitionTimestamp, null);
            
            // Add new state and current timestamp
            conn.add(actionId, hasCurrentState, newState);
            conn.add(actionId, hasLastTransitionTimestamp, vf.createLiteral(java.time.OffsetDateTime.now().toString(), org.eclipse.rdf4j.model.vocabulary.XSD.DATETIME));
            conn.commit();
            log.info("Transitioned action {} to {}", actionId, stateFragment);
        });
    }

    /**
     * Legacy update method for ExecutionStatus (string based).
     */
    public void updateExecutionStatus(IRI actionId, ExecutionStatus status) {
        IRI hasExecutionStatus = ontologyRegistry.moam(OntologyConstants.PROP_HAS_EXECUTION_STATUS);
        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.remove(actionId, hasExecutionStatus, null);
            conn.add(actionId, hasExecutionStatus, vf.createLiteral(status.name()));
            conn.commit();
            log.info("Updated execution status of {} to {}", actionId, status);
        });
    }

    public ActionData fetchActionStructure(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template(TEMPLATE_FETCH_ACTION_STRUCTURE)
                .variable(VAR_ACTION_IRI, actionId)
                .build();

        return sparqlClient.executeQuery(sparql, stream -> {
            Map<IRI, List<BindingSet>> allBindings = stream.collect(
                    Collectors.groupingBy(bs -> (IRI) bs.getValue(VAR_ACTION), LinkedHashMap::new, Collectors.toList())
            );
            Result<ActionData> result = modelMapper.mapAction(actionId, allBindings);
            if (result.isSuccess()) {
                return result.value();
            } else {
                log.error("Failed to map action structure for {}: {}", actionId, result.error());
                return null;
            }
        });
    }

    public List<ActionData.SimpleAction> findActionsForResource(IRI resourceIri) {
        String sparql = sparqlQueryBuilder.builder()
                .template(TEMPLATE_FIND_ACTIONS_FOR_RESOURCE)
                .variable(VAR_RESOURCE_IRI, resourceIri)
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream
                .collect(Collectors.groupingBy(bs -> (IRI) bs.getValue(VAR_ACTION)))
                .entrySet().stream()
                .map(entry -> modelMapper.mapAction(entry.getKey(), Map.of(entry.getKey(), entry.getValue())))
                .filter(Result::isSuccess)
                .map(Result::value)
                .filter(ad -> ad instanceof ActionData.SimpleAction)
                .map(ad -> (ActionData.SimpleAction) ad)
                .toList()
        );
    }

    public void createActionWorkflow(IRI resourceIri, IRI intentIri, String actionId) {
        IRI actionIri = ontologyRegistry.moam(actionId);
        IRI hasCurrentState = ontologyRegistry.moam("hasCurrentState");
        IRI stateInitial = ontologyRegistry.moam("State_Initial");
        IRI targetsEntity = ontologyRegistry.moam("targetsEntity");
        IRI hasActionID = ontologyRegistry.moam("hasActionID");
        IRI hasLastTransitionTimestamp = ontologyRegistry.moam("hasLastTransitionTimestamp");

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.add(actionIri, org.eclipse.rdf4j.model.vocabulary.RDF.TYPE, intentIri);
            conn.add(actionIri, org.eclipse.rdf4j.model.vocabulary.RDF.TYPE, ontologyRegistry.moam("AutonomicAction"));
            conn.add(actionIri, hasCurrentState, stateInitial);
            conn.add(actionIri, targetsEntity, resourceIri);
            conn.add(actionIri, hasActionID, vf.createLiteral(actionId));
            conn.add(actionIri, hasLastTransitionTimestamp, vf.createLiteral(java.time.OffsetDateTime.now().toString(), org.eclipse.rdf4j.model.vocabulary.XSD.DATETIME));
            conn.commit();
            log.info("Created new action workflow {} for resource {} with intent {}", actionIri, resourceIri, intentIri);
        });
    }

    public List<AnomalyTarget> findAnomalies() {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-anomalies")
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream.map(bs -> new AnomalyTarget(
                (IRI) bs.getValue("resource"),
                bs.getValue("resourceName").stringValue(),
                (IRI) bs.getValue("intent")
        )).collect(Collectors.toList()));
    }

    public List<ActiveActionSummary> findActiveActions() {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-active-actions")
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream.map(bs -> new ActiveActionSummary(
                (IRI) bs.getValue("action"),
                (IRI) bs.getValue("type"),
                (IRI) bs.getValue("resource"),
                bs.getValue("resourceName").stringValue(),
                bs.getValue("state").stringValue()
        )).collect(Collectors.toList()));
    }

    public boolean isIdempotencyWindowOpen(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template("check-idempotency")
                .variable("actionIri", actionId)
                .build();

        List<BindingSet> results = sparqlClient.executeQuery(sparql, stream -> stream.collect(Collectors.toList()));
        if (results.isEmpty()) return true; 
        
        BindingSet bs = results.get(0);
        if (bs.getValue("window") == null || bs.getValue("lastTransition") == null) return true;
        
        int window = ((Literal) bs.getValue("window")).intValue();
        java.time.OffsetDateTime lastTransition = java.time.OffsetDateTime.parse(bs.getValue("lastTransition").stringValue());
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        
        return now.isAfter(lastTransition.plusSeconds(window));
    }

    public IRI findCompensation(IRI actionIri) {
        IRI hasCompensation = ontologyRegistry.moam("hasCompensation");
        return sparqlClient.executeWithConnection(conn -> {
            var statements = conn.getStatements(actionIri, hasCompensation, null);
            if (statements.hasNext()) {
                return (IRI) statements.next().getObject();
            }
            return null;
        });
    }

    public boolean executeConditionQuery(String query) {
        return sparqlClient.executeBooleanQuery(query);
    }

    public record ActiveActionSummary(IRI actionIri, IRI typeIri, IRI resourceIri, String resourceName, String stateFragment) {}
    public record AnomalyTarget(IRI resourceIri, String resourceName, IRI intentIri) {}
}
