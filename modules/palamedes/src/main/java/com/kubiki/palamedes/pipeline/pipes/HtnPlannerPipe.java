package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HtnPlannerPipe (MAPE-Plan):
 * Decomposes INITIAL workflows and transitions them to PLANNED state.
 * Materializes sequential steps for ComplexWorkflows in the graph.
 */
@Order(1)
@Component
@RequiredArgsConstructor
public class HtnPlannerPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(HtnPlannerPipe.class);

    private final ActionUtils utils;
    private final GraphDBGateway graphDBGateway;
    private final StateRepository stateRepository;
    private final OntologyRegistry ontologyRegistry;
    private final WorkflowStateMapper mapper;


    @Override
    public boolean process(WorkflowContext context) {
        if (!mapper.getFragment(WorkflowState.INITIAL).equals(context.metadata().get("currentState"))) {
            return true;
        }

        log.info("HTN Planning for action {}", context.actionId());

        ActionData data = context.actionData();
        if (data instanceof ActionData.ComplexWorkflow cw) {
            log.info("Decomposing ComplexWorkflow {}", context.actionId());
            decomposeWorkflow(cw, context.actionId());
        } else if (data instanceof ActionData.SimpleAction sa) {
            log.debug("No decomposition needed for SimpleAction {}", context.actionId());
        }

        boolean success = stateRepository.transition(context.actionId(), WorkflowState.INITIAL, WorkflowState.PLANNED);
        if (success) {
            context.metadata().put("currentState", mapper.getFragment(WorkflowState.PLANNED));
        }
        return !success;
    }

    private void decomposeWorkflow(ActionData.ComplexWorkflow workflow, IRI parentIri) {
        List<ActionData> steps = workflow.steps();
        IRI previousStepIri = null;

        for (ActionData step : steps) {
            // Generate a concrete IRI for this step instance
            String stepId = utils.generateStepId();
            IRI stepIri = ontologyRegistry.actionsOntology(stepId);

            // Materialize the child node (Simple or Complex)
            graphDBGateway.materializeActionInstance(stepIri, step, workflow.target(), parentIri);

            if (previousStepIri != null) {
                // Link to depend on previous sibling
                graphDBGateway.linkDependent(stepIri, previousStepIri);
            } else {
                // The very first child starts in INITIAL state to trigger its own MAPE loop
                graphDBGateway.transitionState(stepIri, mapper.getFragment(WorkflowState.INITIAL));
            }

            previousStepIri = stepIri;
        }
    }
}
