package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * OptimizationPipe (MAPE-Plan):
 * Classifies the action's goal and scope based on MoaMont properties.
 */
@Order(2)
@Component
public class OptimizationPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(OptimizationPipe.class);

    @Override
    @LogLoopStep(
        phase = LoopPhase.PLAN,
        step = "Action Optimization",
        actionId = "#context.actionId().stringValue()",
        resource = "#context.metadata().get('resourceName') != null ? #context.metadata().get('resourceName').toString() : null",
        details = "'functionalIntent=' + #context.actionData().functionalIntent() + ', executionCost=' + #context.actionData().executionCost()"
    )
    public boolean process(WorkflowContext context) {
        // This pipe processes all active actions to provide visibility into their classification

        var data = context.actionData();
        log.debug("OptimizationPipe: Classification:");
        log.debug("  - Functional Intent: {}", data.functionalIntent());
        log.debug("  - Layer Boundary:    {}", data.layerBoundary());
        log.debug("  - Execution Cost:    {}", data.executionCost());

        // In a real industrial engine, we might block or postpone high-cost actions 
        // if they conflict with other running workflows.

        return true; // Always continue
    }
}
