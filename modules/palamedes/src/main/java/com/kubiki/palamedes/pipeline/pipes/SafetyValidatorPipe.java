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
 * Industrial Rule: Logic is delegated to strategies; this pipe is purely orchestral.
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

        // 1. Idempotency Gate (Temporal safety)
        if (!graphDBGateway.isIdempotencyWindowOpen(context.actionId())) {
            log.warn("Action {} is blocked by Idempotency Window", context.actionId());
            return false;
        }

        // 2. Pre-condition Gate (Semantic safety)
        if (!evaluatePreConditions(context)) {
            return false;
        }

        log.info("Action {} safety checks PASSED", context.actionId());
        return !stateRepository.transition(context.actionId(), WorkflowState.PLANNED, WorkflowState.VALIDATED);
    }

    private boolean evaluatePreConditions(WorkflowContext context) {
        for (ActionData.Condition cond : context.actionData().preConditions()) {
            Optional<ConditionStrategy> strategy = conditionFactory.getStrategy(cond.type());
            if (strategy.isPresent()) {
                try {
                    if (!strategy.get().evaluate(cond)) {
                        log.warn("Pre-condition {} NOT MET for action {}", cond.id(), context.actionId());
                        return false;
                    }
                } catch (Exception e) {
                    log.error("Error evaluating pre-condition {}: {}", cond.id(), e.getMessage());
                    return false;
                }
            } else {
                log.error("Unknown condition type: {}", cond.type());
                return false;
            }
        }
        return true;
    }
}
