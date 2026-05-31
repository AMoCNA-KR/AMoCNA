package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.palamedes.dispatcher.DispatcherService;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import com.kubiki.palamedes.planner.PlannerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Order(4)
@Component
@RequiredArgsConstructor
public class ActionDispatcherPipe implements MapePipe {

    private static final Logger log = LoggerFactory.getLogger(ActionDispatcherPipe.class);

    /** Pipeline control keys — not passed to instruction template hydration. */
    private static final Set<String> PIPELINE_METADATA_KEYS = Set.of("currentState");

    private final StateRepository stateRepository;
    private final DispatcherService dispatcherService;
    private final PlannerService plannerService;
    private final WorkflowStateMapper mapper;

    @Override
    public boolean process(WorkflowContext context) {
        if (!mapper.getFragment(WorkflowState.VALIDATED).equals(context.metadata().get("currentState"))) {
            return true;
        }

        log.info("Evaluating dispatch strategies for action {}", context.actionId());

        return switch (context.actionData()) {
            case ActionData.SimpleAction simpleAction -> {
                Map<String, String> hydrationData = hydrationFromContext(context);
                ActionMessage message = plannerService.buildActionMessage(simpleAction, hydrationData);

                dispatcherService.dispatch(message);

                yield stateRepository.transition(context.actionId(), WorkflowState.VALIDATED, WorkflowState.IN_PROGRESS);
            }
            case ActionData.ComplexWorkflow _ -> {
                log.debug("ComplexWorkflow node {} bypasses direct broker routing (HTN pipeline handles children)", context.actionId());
                yield true;
            }
        };
    }

    static Map<String, String> hydrationFromContext(WorkflowContext context) {
        Map<String, String> hydration = new HashMap<>();
        for (Map.Entry<String, Object> entry : context.metadata().entrySet()) {
            if (PIPELINE_METADATA_KEYS.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() != null) {
                hydration.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return hydration;
    }
}
