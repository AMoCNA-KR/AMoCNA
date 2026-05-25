package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import com.kubiki.palamedes.saga.WorkflowPlanner;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

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
            return true;
        }

        log.info("HTN Planning for action {}", context.actionId());

        ActionData data = context.actionData();
        if (data instanceof ActionData.ComplexWorkflow cw) {
            log.info("Decomposing ComplexWorkflow {}", context.actionId());
            workflowPlanner.planWorkflow(cw, context.actionId());
        } else if (data instanceof ActionData.SimpleAction sa) {
            log.debug("No decomposition needed for SimpleAction {}", context.actionId());
        }

        boolean success = stateRepository.transition(context.actionId(), WorkflowState.INITIAL, WorkflowState.PLANNED);
        if (success) {
            context.metadata().put("currentState", mapper.getFragment(WorkflowState.PLANNED));
        }
        return success;
    }
}
