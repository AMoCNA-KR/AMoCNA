package com.kubiki.palamedes.saga;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionStatusUpdate;
import com.kubiki.palamedes.model.ExecutionStatus;
import com.kubiki.palamedes.model.WorkflowState;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * SagaManager (MAPE-Monitor/Analyze):
 * Handles execution feedback from Themis and manages workflow state/compensations.
 * Uses atomic transitions to ensure transactional integrity.
 */
@Service
public class SagaManager {
    private static final Logger log = LoggerFactory.getLogger(SagaManager.class);
    private final GraphDBGateway gateway;
    private final StateRepository stateRepository;
    private final OntologyRegistry ontologyRegistry;

    public SagaManager(GraphDBGateway gateway, StateRepository stateRepository, OntologyRegistry ontologyRegistry) {
        this.gateway = gateway;
        this.stateRepository = stateRepository;
        this.ontologyRegistry = ontologyRegistry;
    }

    public void handleFeedback(ActionStatusUpdate update) {
        log.info("SagaManager: Handling feedback for action {}: {}", update.actionId(), update.status());
        
        IRI actionIri = ontologyRegistry.moam(update.actionId());
        
        if (update.status() == ExecutionStatus.COMPLETED) {
            log.info("SagaManager: Action {} succeeded", update.actionId());
            boolean transitioned = stateRepository.transition(actionIri, WorkflowState.IN_PROGRESS, WorkflowState.SUCCEEDED);
            
            if (transitioned) {
                unlockNextSteps(actionIri);
            }
        } else {
            log.error("SagaManager: Action {} failed with status {}", update.actionId(), update.status());
            boolean transitioned = stateRepository.transition(actionIri, WorkflowState.IN_PROGRESS, WorkflowState.FAILED);
            
            if (transitioned) {
                triggerCompensation(actionIri, update.actionId());
            }
        }
    }

    private void unlockNextSteps(IRI finishedActionIri) {
        log.info("SagaManager: Looking for steps dependent on {}", finishedActionIri);
        List<IRI> dependents = gateway.findDependents(finishedActionIri);
        
        for (IRI dependent : dependents) {
            log.info("SagaManager: Unlocking dependent step {}", dependent);
            // Move to INITIAL so the HTN pipeline picks it up
            gateway.transitionState(dependent, WorkflowState.INITIAL.getFragment());
        }
    }

    private void triggerCompensation(IRI failedActionIri, String actionId) {
        IRI compensationIri = gateway.findCompensation(failedActionIri);
        if (compensationIri != null) {
            log.info("SagaManager: Triggering compensation {} for action {}", compensationIri, actionId);
            
            String compId = "comp-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Fetch original action data to get the target
            var originalAction = gateway.fetchActionStructure(failedActionIri);
            if (originalAction != null) {
                gateway.createActionWorkflow(originalAction.target(), compensationIri, compId);
                log.info("SagaManager: Compensation workflow {} created in State_Initial", compId);
            }
        } else {
            log.warn("SagaManager: No compensation defined for action {}", actionId);
        }
    }
}
