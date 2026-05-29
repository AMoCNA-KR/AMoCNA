package com.kubiki.palamedes.knowledge;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.knowledge.SparqlClient;
import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.model.*;
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GraphDBGateway {
  private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);
  private static final String RESOURCE_IRI = "resource";
  private static final String ACTION_IRI = "action";
  private static final String INTENT_IRI = "intent";
  private static final String HAS_CURRENT_STATE_IRI = "hasCurrentState";
  private static final String HAS_LAST_TRANSITION_TIMESTAMP_IRI = "hasLastTransitionTimestamp";
  private static final String TARGETS_ENTITY_IRI = "targetsEntity";
  private static final String HAS_ACTION_ID_IRI = "hasActionID";
  private static final String IS_DECOMPOSED_INTO_IRI = "isDecomposedInto";
  private static final String HAS_EXECUTION_PROTOCOL_IRI = "hasExecutionProtocol";
  private static final String HAS_EXECUTION_INSTRUCTION_IRI = "hasExecutionInstruction";
  private static final String COMPLEX_WORKFLOW_IRI = "ComplexWorkflow";
  private static final String SIMPLE_ACTION_IRI = "SimpleAction";
  private static final String HAS_HTTP_METHOD_IRI = "hasHttpMethod";
  private static final String HAS_EXPECTED_STATUS_CODE_IRI = "hasExpectedStatusCode";
  private static final String DEPENDS_ON_IRI = "dependsOn";
  private static final String RESOURCE_NAME_VAR = "resourceName";
  private static final String STATE_IRI = "state";
  private static final String WINDOW_IRI = "window";
  private static final String LAST_TRANSITION_IRI = "lastTransition";
  private static final String COMPENSATION_INTENT_IRI = "compensationIntent";
  private static final String ROOT_RESOURCE_IRI = "rootResource";
  private static final String ROOT_RESOURCE_NAME_IRI = "rootResourceName";
  private static final String DEPENDENT_IRI = "dependent";

  private final SparqlClient sparqlClient;
  private final SparqlRepository sparqlRepository;
  private final ModelMapper modelMapper;
  private final WorkflowStateMapper workflowStateMapper;
  private final OntologyRegistry ontologyRegistry;
  private final PalamedesProperties properties;
  private final MeterRegistry meterRegistry;

  public void transitionState(IRI actionId, String stateFragment) {
    IRI hasCurrentState = ontologyRegistry.actionsOntology(HAS_CURRENT_STATE_IRI);
    IRI newState = ontologyRegistry.actionsOntology(stateFragment);
    IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology(HAS_LAST_TRANSITION_TIMESTAMP_IRI);

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
    IRI hasCurrentState = ontologyRegistry.actionsOntology(HAS_CURRENT_STATE_IRI);
    IRI stateInitial = ontologyRegistry.actionsOntology(properties.states().actionStates().get("initial"));
    IRI targetsEntity = ontologyRegistry.actionsOntology(TARGETS_ENTITY_IRI);
    IRI hasActionID = ontologyRegistry.actionsOntology(HAS_ACTION_ID_IRI);
    IRI hasLastTransitionTimestamp = ontologyRegistry.actionsOntology(HAS_LAST_TRANSITION_TIMESTAMP_IRI);

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
    IRI targetsEntity = ontologyRegistry.actionsOntology(TARGETS_ENTITY_IRI);
    IRI hasActionID = ontologyRegistry.actionsOntology(HAS_ACTION_ID_IRI);
    IRI isDecomposedInto = ontologyRegistry.actionsOntology(IS_DECOMPOSED_INTO_IRI);

    sparqlClient.executeWithConnection(conn -> {
      ValueFactory vf = conn.getValueFactory();
      conn.begin();
      conn.add(parentIri, isDecomposedInto, actionIri);
      conn.add(actionIri, RDF.TYPE, template.id());
      conn.add(actionIri, RDF.TYPE, ontologyRegistry
          .actionsOntology(template instanceof ActionData.ComplexWorkflow ? COMPLEX_WORKFLOW_IRI : SIMPLE_ACTION_IRI));
      conn.add(actionIri, targetsEntity, target);
      conn.add(actionIri, hasActionID, vf.createLiteral(actionIri.getLocalName()));

      if (template instanceof ActionData.SimpleAction sa) {
        conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXECUTION_PROTOCOL_IRI),
            vf.createLiteral(sa.protocol().name()));
        conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXECUTION_INSTRUCTION_IRI),
            vf.createLiteral(sa.instruction()));
        if (sa.method() != null) {
          conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_HTTP_METHOD_IRI),
              vf.createLiteral(sa.method().name()));
        }
        conn.add(actionIri, ontologyRegistry.actionsOntology(HAS_EXPECTED_STATUS_CODE_IRI),
            vf.createLiteral(String.valueOf(sa.expectedStatusCode()), XSD.INTEGER));
      }

      conn.commit();
      log.info("Materialized action instance {} under parent {}", actionIri, parentIri);
    });
  }

  public WorkflowState getState(IRI actionIri) {
    IRI hasCurrentState = ontologyRegistry.actionsOntology(HAS_CURRENT_STATE_IRI);
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
    IRI dependsOn = ontologyRegistry.actionsOntology(DEPENDS_ON_IRI);
    sparqlClient.executeWithConnection(conn -> {
      conn.begin();
      conn.add(dependent, dependsOn, dependency);
      conn.commit();
      log.info("Linked {} to depend on {}", dependent, dependency);
    });
  }

  public ActionData fetchActionStructure(IRI actionId) {
    List<BindingSet> allBindings = sparqlRepository.fetchActionStructure(actionId.stringValue());

    Map<IRI, List<BindingSet>> grouped = allBindings.stream().collect(
        Collectors.groupingBy(bs -> (IRI) bs.getValue(ACTION_IRI), LinkedHashMap::new, Collectors.toList()));
    Result<ActionData> result = modelMapper.mapAction(actionId, grouped);
    if (result.isSuccess()) {
      return result.value();
    } else {
      log.error("Failed to map action structure for {}: {}", actionId, result.error());
      return null;
    }
  }

  public Map<IRI, ActionData> fetchActionStructures(List<IRI> actionIds) {
    String joinedIds = actionIds.stream()
        .map(iri -> "<" + iri.stringValue() + ">")
        .collect(Collectors.joining(" "));

    List<BindingSet> allBindings = sparqlRepository.fetchActionStructures(joinedIds);

    Map<IRI, List<BindingSet>> grouped = allBindings.stream().collect(
        Collectors.groupingBy(bs -> (IRI) bs.getValue(ACTION_IRI), LinkedHashMap::new, Collectors.toList()));
    return modelMapper.mapActions(grouped, actionIds);
  }

  public List<ActiveActionSummary> findActiveActions() {
    return sparqlRepository.findActiveActions().stream().map(bs -> new ActiveActionSummary(
        (IRI) bs.getValue(ACTION_IRI),
        (IRI) bs.getValue(RESOURCE_IRI),
        bs.getValue(RESOURCE_NAME_VAR).stringValue(),
        bs.getValue(STATE_IRI).stringValue())).collect(Collectors.toList());
  }

  public boolean isIdempotencyWindowOpen(IRI actionId) {
    List<BindingSet> results = sparqlRepository.checkIdempotency(actionId.stringValue());
    if (results.isEmpty())
      return true;

    BindingSet bs = results.getFirst();
    if (bs.getValue(WINDOW_IRI) == null || bs.getValue(LAST_TRANSITION_IRI) == null)
      return true;

    int window = ((Literal) bs.getValue(WINDOW_IRI)).intValue();
    OffsetDateTime lastTransition = OffsetDateTime.parse(bs.getValue(LAST_TRANSITION_IRI).stringValue());
    OffsetDateTime now = OffsetDateTime.now();

    return now.isAfter(lastTransition.plusSeconds(window));
  }

  public IRI findCompensation(IRI actionId) {
    List<BindingSet> results = sparqlRepository.findCompensation(actionId.stringValue());
    if (!results.isEmpty()) {
      return (IRI) results.getFirst().getValue(COMPENSATION_INTENT_IRI);
    }
    return null;
  }

  public List<AnomalyTarget> findAnomalies() {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return sparqlRepository.findAnomalies().stream().map(bs -> new AnomalyTarget(
        (IRI) bs.getValue(RESOURCE_IRI),
        bs.getValue(RESOURCE_NAME_VAR).stringValue(),
        (IRI) bs.getValue(INTENT_IRI))).collect(Collectors.toList());
    } finally {
      sample.stop(Timer.builder("amocna.semantic.query.anomalies").register(meterRegistry));
    }
  }

  public List<AnomalyTarget> findRootCause(IRI startResource) {
    return sparqlRepository.findRootCause(startResource.stringValue()).stream().map(bs -> new AnomalyTarget(
        (IRI) bs.getValue(ROOT_RESOURCE_IRI),
        bs.getValue(ROOT_RESOURCE_NAME_IRI).stringValue(),
        (IRI) bs.getValue(INTENT_IRI))).collect(Collectors.toList());
    } finally {
      sample.stop(Timer.builder("amocna.semantic.query.anomalies").register(meterRegistry));
    }
  }

  public List<IRI> findDependents(IRI actionId) {
    return sparqlRepository.findDependents(actionId.stringValue()).stream()
        .map(bs -> (IRI) bs.getValue(DEPENDENT_IRI))
        .collect(Collectors.toList());
  }

  public void updateResourceState(String resourceIri, String stateIri) {
    sparqlRepository.updateResourceState(resourceIri, stateIri);
    log.info("Set resource state: {} → {}", resourceIri, stateIri);
  }

  public IRI findParent(IRI childIri) {
    IRI isDecomposedInto = ontologyRegistry.actionsOntology(IS_DECOMPOSED_INTO_IRI);
    return sparqlClient.executeWithConnection(conn -> {
      var statements = conn.getStatements(null, isDecomposedInto, childIri);
      if (statements.hasNext()) {
        return (IRI) statements.next().getSubject();
      }
      return null;
    });
  }

  public List<IRI> findChildren(IRI parentIri) {
    IRI isDecomposedInto = ontologyRegistry.actionsOntology(IS_DECOMPOSED_INTO_IRI);
    return sparqlClient.executeWithConnection(conn -> {
      return conn.getStatements(parentIri, isDecomposedInto, null)
          .stream().map(s -> (IRI) s.getObject()).toList();
    });
  }

  public boolean executeConditionQuery(String query) {
    return sparqlClient.executeBooleanQuery(query);
  }
}
