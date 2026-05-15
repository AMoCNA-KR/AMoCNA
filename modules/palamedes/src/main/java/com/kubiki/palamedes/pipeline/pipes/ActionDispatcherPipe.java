package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.dispatcher.DispatcherService;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActionMessage;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.planner.PlannerService;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ActionDispatcherPipe (MAPE-Execute):
 * Hydrates and dispatches actions that are VALIDATED.
 */
@Order(4)
@Component
public class ActionDispatcherPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(ActionDispatcherPipe.class);
    private final StateRepository stateRepository;
    private final DispatcherService dispatcherService;
    private final PlannerService plannerService;

    public ActionDispatcherPipe(StateRepository stateRepository, DispatcherService dispatcherService, PlannerService plannerService) {
        this.stateRepository = stateRepository;
        this.dispatcherService = dispatcherService;
        this.plannerService = plannerService;
    }

    @Override
    public boolean process(WorkflowContext context) {
        if (!"State_Validated".equals(context.metadata().get("currentState"))) {
            return true;
        }

        log.info("Dispatching action {}", context.actionId());

        if (context.actionData() instanceof ActionData.SimpleAction simpleAction) {
            Map<String, String> hydrationData = Map.of("resourceName", (String) context.metadata().get("resourceName"));
            ActionMessage message = plannerService.buildActionMessage(simpleAction, hydrationData);
            
            // Dispatch to RabbitMQ
            dispatcherService.dispatch(message);
            
            // Transition to InProgress
            return !stateRepository.transition(context.actionId(), WorkflowState.VALIDATED, WorkflowState.IN_PROGRESS);
        }

        return true;
    }
}
