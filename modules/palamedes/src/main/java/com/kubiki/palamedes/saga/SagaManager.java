package com.kubiki.palamedes.saga;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.model.ActionStatusUpdate;
import com.kubiki.palamedes.model.ExecutionStatus;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SagaManager (MAPE-Monitor/Analyze):
 * Handles execution feedback from Themis and manages workflow state/compensations.
 */
@Service
public class SagaManager {
    private static final Logger log = LoggerFactory.getLogger(SagaManager.class);
    private final GraphDBGateway gateway;
    private final OntologyRegistry ontologyRegistry;

    public SagaManager(GraphDBGateway gateway, OntologyRegistry ontologyRegistry) {
        this.gateway = gateway;
        this.ontologyRegistry = ontologyRegistry;
    }

    public void handleFeedback(ActionStatusUpdate update) {
        log.info("SagaManager: Handling feedback for action {}: {}", update.actionId(), update.status());
        
        IRI actionIri = ontologyRegistry.moam(update.actionId());
        
        if (update.status() == ExecutionStatus.COMPLETED) {
            log.info("SagaManager: Action {} succeeded", update.actionId());
            gateway.transitionState(actionIri, "State_Succeeded");
            // TODO: If part of a complex workflow, trigger next step
        } else {
            log.error("SagaManager: Action {} failed with status {}", update.actionId(), update.status());
            gateway.transitionState(actionIri, "State_Failed");
            
            // Trigger Compensation (Rollback)
            IRI compensationIri = gateway.findCompensation(actionIri);
            if (compensationIri != null) {
                log.info("SagaManager: Triggering compensation {} for action {}", compensationIri, update.actionId());
                
                String compId = "comp-" + UUID.randomUUID().toString().substring(0, 8);
                // In a real system, we'd copy target/resource info from the failed action
                // For now, we transition the graph state of the compensation individual if it exists
                // or create a new one. Let's assume we create a new one linked to the same resource.
                
                // Fetch the original resource for the failed action
                var originalAction = gateway.fetchActionStructure(actionIri);
                if (originalAction != null) {
                    gateway.createActionWorkflow(originalAction.target(), compensationIri, compId);
                    log.info("SagaManager: Compensation workflow {} created in State_Initial", compId);
                }
            } else {
                log.warn("SagaManager: No compensation defined for action {}", update.actionId());
            }
        }
    }
}
