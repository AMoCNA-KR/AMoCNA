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
        if (!"State_Initial".equals(context.metadata().get("currentState"))) {
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

            if (step instanceof ActionData.SimpleAction sa) {
                graphDBGateway.materializeSimpleAction(stepIri, sa, workflow.target());
            } else if (step instanceof ActionData.ComplexWorkflow cw) {
                // Recursively handle nested workflows
                // Note: This would require a more complex materialization in GraphDBGateway
                // For simplicity in Phase 1, we focus on workflows of simple actions
                log.warn("Nested ComplexWorkflows not fully implemented in decomposition pipe");
            }

            // Link to parent (optional, but good for traceability)
            // graphDBGateway.linkToParent(stepIri, parentIri);

            if (previousStepIri != null) {
                graphDBGateway.linkDependent(stepIri, previousStepIri);
            } else {
                // First step starts in INITIAL state to trigger the loop
                graphDBGateway.transitionState(stepIri, WorkflowState.INITIAL.getFragment());
            }

            previousStepIri = stepIri;
        }
    }
}
