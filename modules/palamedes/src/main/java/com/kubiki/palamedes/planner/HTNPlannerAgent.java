package com.kubiki.palamedes.planner;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HTNPlannerAgent (MAPE-Plan):
 * Monitors State_Initial workflows and decomposes them into primitive SimpleActions.
 */
@Service
public class HTNPlannerAgent {
    private static final Logger log = LoggerFactory.getLogger(HTNPlannerAgent.class);
    private final GraphDBGateway gateway;

    public HTNPlannerAgent(GraphDBGateway gateway) {
        this.gateway = gateway;
    }

    @Scheduled(fixedRate = 5000)
    public void plan() {
        log.debug("HTNPlannerAgent: Checking for workflows in State_Initial...");
        
        List<GraphDBGateway.ActionSummary> initialActions = gateway.findActionsByState("State_Initial");
        
        for (GraphDBGateway.ActionSummary action : initialActions) {
            log.info("HTNPlannerAgent: Planning action {} (type: {})", action.actionIri(), action.typeIri());
            
            // 1. Fetch the full structure (blueprint)
            ActionData structure = gateway.fetchActionStructure(action.actionIri());
            
            if (structure instanceof ActionData.ComplexWorkflow workflow) {
                log.info("HTNPlannerAgent: Decomposing complex workflow {}", action.actionIri());
                // TODO: HTN decomposition logic (creating child nodes in the graph)
            } else {
                log.debug("HTNPlannerAgent: {} is a simple action, no decomposition needed", action.actionIri());
            }
            
            // 2. Transition to State_Planned
            gateway.transitionState(action.actionIri(), "State_Planned");
        }
    }
}
