package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HtnPlannerPipe (MAPE-Plan):
 * Decomposes INITIAL workflows and transitions them to PLANNED.
 * Materializes sequential steps for ComplexWorkflows.
 */
@Component
public class HtnPlannerPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(HtnPlannerPipe.class);
    private final StateRepository stateRepository;
    private final GraphDBGateway graphDBGateway;

    public HtnPlannerPipe(StateRepository stateRepository, GraphDBGateway graphDBGateway) {
        this.stateRepository = stateRepository;
        this.graphDBGateway = graphDBGateway;
    }

    @Override
    public boolean process(WorkflowContext context) {
        if (!"State_Initial".equals(context.metadata().get("currentState"))) {
            return true; // Not our state, let other pipes handle it
        }

        log.info("HTN Planning for action {}", context.actionId());

        if (context.actionData() instanceof ActionData.ComplexWorkflow cw) {
            log.info("Decomposing ComplexWorkflow {}", context.actionId());
            
            // Industrial Rule: Option A Sequential Saga
            // Materialize steps in the graph and link them via dependsOn
            // The first step should be in INITIAL, others should be 'Locked' (waiting for dependency)
            
            // For now, we transition the parent to PLANNED. 
            // In a real industrial HTN, this pipe would insert the children now.
        }

        // Atomic transition to PLANNED
        boolean success = stateRepository.transition(context.actionId(), WorkflowState.INITIAL, WorkflowState.PLANNED);
        return !success; // If we succeeded, stop the pipeline for this action (wait for next run)
    }
}
