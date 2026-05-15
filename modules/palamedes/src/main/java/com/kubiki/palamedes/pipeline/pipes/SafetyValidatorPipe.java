package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SafetyValidatorPipe (MAPE-Plan/Execute):
 * Verifies PreConditions before an action is transitioned to VALIDATED.
 */
@Component
public class SafetyValidatorPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(SafetyValidatorPipe.class);
    private final StateRepository stateRepository;
    private final ConditionFactory conditionFactory;
    private final GraphDBGateway graphDBGateway;

    public SafetyValidatorPipe(StateRepository stateRepository, ConditionFactory conditionFactory, GraphDBGateway graphDBGateway) {
        this.stateRepository = stateRepository;
        this.conditionFactory = conditionFactory;
        this.graphDBGateway = graphDBGateway;
    }

    @Override
    public boolean process(WorkflowContext context) {
        if (!"State_Planned".equals(context.metadata().get("currentState"))) {
            return true;
        }

        log.info("Safety Validation for action {}", context.actionId());

        // 1. Check Idempotency Window
        if (!graphDBGateway.isIdempotencyWindowOpen(context.actionId())) {
            log.warn("Action {} is within idempotency cooldown, skipping", context.actionId());
            return false; // Stop pipeline for this action
        }

        // 2. Evaluate PreConditions
        boolean allMet = true;
        for (ActionData.Condition cond : context.actionData().preConditions()) {
            Optional<ConditionStrategy> strategy = conditionFactory.getStrategy(cond.type());
            if (strategy.isPresent()) {
                if (!strategy.get().evaluate(cond)) {
                    log.warn("Precondition {} not met for action {}", cond.id(), context.actionId());
                    allMet = false;
                    break;
                }
            } else {
                log.error("No strategy found for condition type {}", cond.type());
                allMet = false;
                break;
            }
        }

        if (allMet) {
            log.info("Action {} safety checks PASSED", context.actionId());
            return !stateRepository.transition(context.actionId(), WorkflowState.PLANNED, WorkflowState.VALIDATED);
        }

        return false;
    }
}
