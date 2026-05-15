package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * HtnPlannerPipe (MAPE-Plan):
 * Decomposes INITIAL workflows and transitions them to PLANNED.
 * Materializes sequential steps for ComplexWorkflows in the graph.
 * Industrial Rule: Incremental Decomposition. Only expands one level at a time.
 */
@Component
public class HtnPlannerPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(HtnPlannerPipe.class);
    private final StateRepository stateRepository;
    private final GraphDBGateway graphDBGateway;
    private final OntologyRegistry ontologyRegistry;

    public HtnPlannerPipe(StateRepository stateRepository, GraphDBGateway graphDBGateway, OntologyRegistry ontologyRegistry) {
        this.stateRepository = stateRepository;
        this.graphDBGateway = graphDBGateway;
        this.ontologyRegistry = ontologyRegistry;
    }

    @Override
    public boolean process(WorkflowContext context) {
        if (!WorkflowState.INITIAL.getFragment().equals(context.metadata().get("currentState"))) {
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

        // Atomic transition to PLANNED
        return !stateRepository.transition(context.actionId(), WorkflowState.INITIAL, WorkflowState.PLANNED);
    }

    private void decomposeWorkflow(ActionData.ComplexWorkflow workflow, IRI parentIri) {
        List<ActionData> steps = workflow.steps();
        IRI previousStepIri = null;

        for (ActionData step : steps) {
            // Generate a concrete IRI for this step instance
            String stepId = "step-" + UUID.randomUUID().toString().substring(0, 8);
            IRI stepIri = ontologyRegistry.moam(stepId);

            // Materialize the child node (Simple or Complex)
            graphDBGateway.materializeActionInstance(stepIri, step, workflow.target(), parentIri);

            // Sequential Locking (Option A: Sequential Saga)
            if (previousStepIri != null) {
                // Link to depend on previous sibling
                graphDBGateway.linkDependent(stepIri, previousStepIri);
            } else {
                // The very first child starts in INITIAL state to trigger its own MAPE loop
                graphDBGateway.transitionState(stepIri, WorkflowState.INITIAL.getFragment());
            }

            previousStepIri = stepIri;
        }
    }
}
