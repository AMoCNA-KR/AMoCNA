package com.kubiki.themis.saga;

import com.kubiki.themis.execution.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class SagaEngine {
    private static final Logger logger = LoggerFactory.getLogger(SagaEngine.class);
    private final List<Step> steps = new ArrayList<>();

    public record Step(String name, ActionExecutor executor, String targetId) {}

    public void addStep(Step step) {
        steps.add(step);
    }

    public boolean run() {
        List<Step> executedSteps = new ArrayList<>();
        try {
            for (Step step : steps) {
                logger.info("Executing step: {}", step.name());
                if (step.executor().execute(step.targetId())) {
                    executedSteps.add(step);
                } else {
                    throw new RuntimeException("Failure at step: " + step.name());
                }
            }
            logger.info("Saga executed successfully");
            return true;
        } catch (Exception e) {
            logger.error("Saga failed: {}. Starting compensation...", e.getMessage());
            compensate(executedSteps);
            return false;
        }
    }

    private void compensate(List<Step> executedSteps) {
        // Compensate in reverse order
        for (int i = executedSteps.size() - 1; i >= 0; i--) {
            Step step = executedSteps.get(i);
            logger.info("Compensating step: {}", step.name());
            try {
                step.executor().compensate(step.targetId());
            } catch (Exception e) {
                logger.error("Compensation failed for step: {}", step.name(), e);
            }
        }
    }
}
