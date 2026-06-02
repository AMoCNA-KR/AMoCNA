package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import com.kubiki.palamedes.saga.WorkflowPlanner;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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

    private final WorkflowPlanner workflowPlanner;
    private final StateRepository stateRepository;
    private final WorkflowStateMapper mapper;


    @Override
    public boolean process(WorkflowContext context) {
        if (!mapper.getFragment(WorkflowState.INITIAL).equals(context.metadata().get("currentState"))) {
            log.info("HtnPlannerPipe: Action is not in State_Initial, skipping");
            return true;
        }

        log.info("HtnPlannerPipe: Starting HTN Planning");

        ActionData data = context.actionData();
        if (data instanceof ActionData.ComplexWorkflow cw) {
            log.info("HtnPlannerPipe: Decomposing ComplexWorkflow");
            workflowPlanner.planWorkflow(cw, context.actionId());
        } else if (data instanceof ActionData.SimpleAction sa) {
            log.info("HtnPlannerPipe: No decomposition needed for SimpleAction");
        }

        log.info("HtnPlannerPipe: Transitioning action from State_Initial to State_Planned");
        boolean success = stateRepository.transition(context.actionId(), WorkflowState.INITIAL, WorkflowState.PLANNED);
        if (success) {
            context.metadata().put("currentState", mapper.getFragment(WorkflowState.PLANNED));
            log.info("HtnPlannerPipe: Successfully transitioned action to State_Planned");
        } else {
            log.error("HtnPlannerPipe: Failed to transition action to State_Planned");
        }
        return success;
    }
}
