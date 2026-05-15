package com.kubiki.palamedes.condition;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SafetyAgent (MAPE-Plan/Execute):
 * Verifies PreConditions and Idempotency Windows before an action is dispatched.
 */
@Service
public class SafetyAgent {
    private static final Logger log = LoggerFactory.getLogger(SafetyAgent.class);
    private final GraphDBGateway gateway;
    private final ConditionEvaluator conditionEvaluator;

    public SafetyAgent(GraphDBGateway gateway, ConditionEvaluator conditionEvaluator) {
        this.gateway = gateway;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Scheduled(fixedRate = 5000)
    public void validate() {
        log.debug("SafetyAgent: Checking for actions in State_Planned...");
        
        List<GraphDBGateway.ActionSummary> plannedActions = gateway.findActionsByState("State_Planned");
        
        for (GraphDBGateway.ActionSummary summary : plannedActions) {
            log.info("SafetyAgent: Validating action {}", summary.actionIri());
            
            // 1. Check Idempotency Window
            if (!gateway.isIdempotencyWindowOpen(summary.actionIri())) {
                log.warn("SafetyAgent: Action {} is within idempotency cooldown, skipping", summary.actionIri());
                continue;
            }
            
            // 2. Evaluate hasPreCondition
            ActionData actionData = gateway.fetchActionStructure(summary.actionIri());
            boolean preconditionsMet = true;
            
            if (actionData != null && !actionData.preConditions().isEmpty()) {
                for (ActionData.Condition condition : actionData.preConditions()) {
                    try {
                        if (!conditionEvaluator.evaluate(condition)) {
                            log.warn("SafetyAgent: Precondition {} not met for action {}", condition.id(), summary.actionIri());
                            preconditionsMet = false;
                            break;
                        }
                    } catch (Exception e) {
                        log.error("SafetyAgent: Error evaluating precondition {}: {}", condition.id(), e.getMessage());
                        preconditionsMet = false;
                        break;
                    }
                }
            }
            
            if (preconditionsMet) {
                log.info("SafetyAgent: Action {} validated successfully", summary.actionIri());
                gateway.transitionState(summary.actionIri(), "State_Validated");
            } else {
                // If conditions not met, we stay in Planned or transition to Failed if needed
                // For now, we just wait for the next cycle
            }
        }
    }
}
