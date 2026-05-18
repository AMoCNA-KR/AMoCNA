package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.*;
import com.kubiki.palamedes.templating.types.IriType;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.BindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GraphDBGateway {
    private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);

    private static final String TEMPLATE_FETCH_ACTION_STRUCTURE = "fetch-action-structure";
    private static final String VAR_ACTION = "action";
    private static final String VAR_ACTION_ID = "actionId";
    public static final String RESOURCE_IRI = "resource";
    public static final String ACTION_IRI = "action";
    public static final String INTENT_IRI = "intent";

    private final SparqlClient sparqlClient;
    private final SparqlQueryBuilder sparqlQueryBuilder;
    private final ModelMapper modelMapper;
    private final WorkflowStateMapper workflowStateMapper;
    private final OntologyRegistry ontologyRegistry;


    public void transitionState(IRI actionId, String stateFragment) {
        IRI hasCurrentState = ontologyRegistry.actionsOntology("hasCurrentState");
        IRI newState = ontologyRegistry.actionsOntology(stateFragment);
        IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology("hasLastTransitionTimestamp");

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.remove(actionId, hasCurrentState, null);
            conn.remove(actionId, hasLastTransitionTimestamp, null);
            conn.add(actionId, hasCurrentState, newState);
            conn.add(actionId, hasLastTransitionTimestamp, vf.createLiteral(OffsetDateTime.now().toString(), XSD.DATETIME));
            conn.commit();
            log.info("Transitioned action {} to {}", actionId, stateFragment);
        });
    }

    public void createActionWorkflow(IRI resourceIri, IRI intentIri, String actionId) {
        IRI actionIri = ontologyRegistry.actionsOntology(actionId);
        IRI hasCurrentState = ontologyRegistry.actionsOntology("hasCurrentState");
        IRI stateInitial = ontologyRegistry.actionsOntology("State_Initial");
        IRI targetsEntity = ontologyRegistry.actionsOntology("targetsEntity");
        IRI hasActionID = ontologyRegistry.actionsOntology("hasActionID");
        IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology("hasLastTransitionTimestamp");

        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.add(actionIri, RDF.TYPE, intentIri);
            conn.add(actionIri, RDF.TYPE, ontologyRegistry.actionsOntology("AutonomicAction"));
            conn.add(actionIri, hasCurrentState, stateInitial);
            conn.add(actionIri, targetsEntity, resourceIri);
            conn.add(actionIri, hasActionID, vf.createLiteral(actionId));
            conn.add(actionIri, hasLastTransitionTimestamp, vf.createLiteral(OffsetDateTime.now().toString(), XSD.DATETIME));
            conn.commit();
            log.info("Created new action workflow {} for resource {} with intent {}", actionIri, resourceIri, intentIri);
        });
    }

    public void materializeActionInstance(IRI actionIri, ActionData template, IRI target, IRI parentIri) {
        IRI targetsEntity = ontologyRegistry.actionsOntology("targetsEntity");
        IRI hasActionID = ontologyRegistry.actionsOntology("hasActionID");
        IRI isDecomposedInto = ontologyRegistry.actionsOntology("isDecomposedInto");
        
        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            conn.begin();
            conn.add(parentIri, isDecomposedInto, actionIri);
            conn.add(actionIri, RDF.TYPE, template.id()); 
            conn.add(actionIri, RDF.TYPE, ontologyRegistry.actionsOntology(template instanceof ActionData.ComplexWorkflow ? "ComplexWorkflow" : "SimpleAction"));
            conn.add(actionIri, targetsEntity, target);
            conn.add(actionIri, hasActionID, vf.createLiteral(actionIri.getLocalName()));

            if (template instanceof ActionData.SimpleAction sa) {
                conn.add(actionIri, ontologyRegistry.actionsOntology("hasExecutionProtocol"), vf.createLiteral(sa.protocol().name()));
                conn.add(actionIri, ontologyRegistry.actionsOntology("hasExecutionInstruction"), vf.createLiteral(sa.instruction()));
                if (sa.method() != null) {
                    conn.add(actionIri, ontologyRegistry.actionsOntology("hasHttpMethod"), vf.createLiteral(sa.method().name()));
                }
                conn.add(actionIri, ontologyRegistry.actionsOntology("hasExpectedStatusCode"), vf.createLiteral(String.valueOf(sa.expectedStatusCode()), XSD.INTEGER));
            }
            
            conn.commit();
            log.info("Materialized action instance {} under parent {}", actionIri, parentIri);
        });
    }

    public WorkflowState getState(IRI actionIri) {
        IRI hasCurrentState = ontologyRegistry.actionsOntology("hasCurrentState");
        return sparqlClient.executeWithConnection(conn -> {
            var statements = conn.getStatements(actionIri, hasCurrentState, null);
            if (statements.hasNext()) {
                IRI stateIri = (IRI) statements.next().getObject();
                return workflowStateMapper.fromFragment(stateIri.getLocalName());
            }
            return null;
        });
    }

    public void linkDependent(IRI dependent, IRI dependency) {
        IRI dependsOn = ontologyRegistry.actionsOntology("dependsOn");
        sparqlClient.executeWithConnection(conn -> {
            conn.begin();
            conn.add(dependent, dependsOn, dependency);
            conn.commit();
            log.info("Linked {} to depend on {}", dependent, dependency);
        });
    }

    public ActionData fetchActionStructure(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template(TEMPLATE_FETCH_ACTION_STRUCTURE)
                .variable(new IriType(VAR_ACTION_ID, actionId))
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

    public List<ActiveActionSummary> findActiveActions() {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-active-actions")
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream.map(bs -> new ActiveActionSummary(
                (IRI) bs.getValue(ACTION_IRI),
                (IRI) bs.getValue(RESOURCE_IRI),
                bs.getValue("resourceName").stringValue(),
                bs.getValue("state").stringValue()
        )).collect(Collectors.toList()));
    }

    public boolean isIdempotencyWindowOpen(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template("check-idempotency")
                .variable(new IriType(VAR_ACTION_ID, actionId))
                .build();

        List<BindingSet> results = sparqlClient.executeQuery(sparql, stream -> stream.collect(Collectors.toList()));
        if (results.isEmpty()) return true; 
        
        BindingSet bs = results.getFirst();
        if (bs.getValue("window") == null || bs.getValue("lastTransition") == null) return true;
        
        int window = ((Literal) bs.getValue("window")).intValue();
        OffsetDateTime lastTransition = OffsetDateTime.parse(bs.getValue("lastTransition").stringValue());
        OffsetDateTime now = OffsetDateTime.now();
        
        return now.isAfter(lastTransition.plusSeconds(window));
    }

    public IRI findCompensation(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-compensation")
                .variable(new IriType(VAR_ACTION_ID, actionId))
                .build();
        List<BindingSet> results = sparqlClient.executeQuery(sparql, Stream::toList);
        if (!results.isEmpty()) {
            return (IRI) results.getFirst().getValue("compensationIntent");
        }
        return null;
    }

    public List<AnomalyTarget> findAnomalies() {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-anomalies")
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream.map(bs -> new AnomalyTarget(
                (IRI) bs.getValue(RESOURCE_IRI),
                bs.getValue("resourceName").stringValue(),
                (IRI) bs.getValue(INTENT_IRI)
        )).collect(Collectors.toList()));
    }

    public List<IRI> findDependents(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-dependents")
                .variable(new IriType(VAR_ACTION_ID, actionId))
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream
                .map(bs -> (IRI) bs.getValue("dependent"))
                .collect(Collectors.toList()));
    }

    public IRI findParent(IRI childIri) {
        IRI isDecomposedInto = ontologyRegistry.actionsOntology("isDecomposedInto");
        return sparqlClient.executeWithConnection(conn -> {
            var statements = conn.getStatements(null, isDecomposedInto, childIri);
            if (statements.hasNext()) {
                return (IRI) statements.next().getSubject();
            }
            return null;
        });
    }

    public List<IRI> findChildren(IRI parentIri) {
        IRI isDecomposedInto = ontologyRegistry.actionsOntology("isDecomposedInto");
        return sparqlClient.executeWithConnection(conn -> {
            return conn.getStatements(parentIri, isDecomposedInto, null)
                    .stream().map(s -> (IRI) s.getObject()).toList();
        });
    }

    public boolean executeConditionQuery(String query) {
        return sparqlClient.executeBooleanQuery(query);
    }

    /**
     * Sets the {@code cnee:hasCurrentState} of a resource to the given state IRI.
     * Uses an atomic DELETE/INSERT to enforce the functional property constraint
     * (exactly one state at a time).
     *
     * <p>Called by {@link com.kubiki.palamedes.prometheus.PrometheusThresholdEvaluator}
     * when a Prometheus threshold is crossed.
     *
     * @param resourceIri fully-qualified IRI of the resource (e.g. cnee:Pod_default_my-pod)
     * @param stateIri    fully-qualified IRI of the new state (e.g. cnee:ContainerCPUThrottledState)
     */
    public void setResourceState(String resourceIri, String stateIri) {
        String cneeNs = ontologyRegistry.getCneeNamespace();
        String sparql = """
                PREFIX cnee: <%s>
                DELETE { <%s> cnee:hasCurrentState ?old }
                INSERT { <%s> cnee:hasCurrentState <%s> }
                WHERE  { OPTIONAL { <%s> cnee:hasCurrentState ?old } }
                """.formatted(cneeNs, resourceIri, resourceIri, stateIri, resourceIri);

        sparqlClient.executeWithConnection(conn -> {
            conn.prepareUpdate(sparql).execute();
            log.info("Set resource state: {} → {}", resourceIri, stateIri);
        });
    }
}
