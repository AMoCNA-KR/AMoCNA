package com.kubiki.palamedes.dispatcher;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActionMessage;
import com.kubiki.palamedes.planner.PlannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * DispatchAgent (MAPE-Execute):
 * Hydrates validated actions and sends them to Themis via RabbitMQ.
 */
@Service
public class DispatchAgent {
    private static final Logger log = LoggerFactory.getLogger(DispatchAgent.class);
    private final GraphDBGateway gateway;
    private final DispatcherService dispatcherService;
    private final PlannerService plannerService;

    public DispatchAgent(GraphDBGateway gateway, 
                         DispatcherService dispatcherService, 
                         PlannerService plannerService) {
        this.gateway = gateway;
        this.dispatcherService = dispatcherService;
        this.plannerService = plannerService;
    }

    @Scheduled(fixedRate = 5000)
    public void dispatch() {
        log.debug("DispatchAgent: Checking for actions in State_Validated...");
        
        List<GraphDBGateway.ActionSummary> validatedActions = gateway.findActionsByState("State_Validated");
        
        for (GraphDBGateway.ActionSummary summary : validatedActions) {
            log.info("DispatchAgent: Dispatching action {}", summary.actionIri());
            
            ActionData structure = gateway.fetchActionStructure(summary.actionIri());
            
            if (structure instanceof ActionData.SimpleAction simpleAction) {
                // Build context data for hydration (e.g. resourceName)
                Map<String, String> contextData = Map.of("resourceName", summary.resourceName());
                
                ActionMessage message = plannerService.buildActionMessage(simpleAction, contextData);
                
                dispatcherService.dispatch(message);
                gateway.transitionState(summary.actionIri(), "State_InProgress");
            } else {
                log.warn("DispatchAgent: Action {} is not a SimpleAction (HTN might not be complete), skipping dispatch", summary.actionIri());
            }
        }
    }
}
