package com.kubiki.palamedes.saga;

import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.OntologyRegistry;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActionStatusUpdate;
import com.kubiki.palamedes.model.ExecutionStatus;
import com.kubiki.palamedes.model.WorkflowState;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SagaManager (MAPE-Monitor/Analyze):
 * Handles execution feedback from Themis and manages workflow state/compensations.
 * Evaluates Post-conditions for verification.
 */
@Service
public class SagaManager {
    private static final Logger log = LoggerFactory.getLogger(SagaManager.class);
    private final GraphDBGateway gateway;
    private final StateRepository stateRepository;
    private final OntologyRegistry ontologyRegistry;
    private final ConditionFactory conditionFactory;

    public SagaManager(GraphDBGateway gateway, 
                       StateRepository stateRepository, 
                       OntologyRegistry ontologyRegistry,
                       ConditionFactory conditionFactory) {
        this.gateway = gateway;
        this.stateRepository = stateRepository;
        this.ontologyRegistry = ontologyRegistry;
        this.conditionFactory = conditionFactory;
    }

    public void handleFeedback(ActionStatusUpdate update) {
        log.info("[SagaManager] Handling feedback for action {}: {}", update.actionId(), update.status());
        
        IRI actionIri = ontologyRegistry.moam(update.actionId());
        
        if (update.status() == ExecutionStatus.COMPLETED) {
            // 1. VERIFICATION: Evaluate Post-conditions
            if (verifyPostConditions(actionIri)) {
                log.info("[SagaManager] Action {} succeeded and verified", update.actionId());
                boolean transitioned = stateRepository.transition(actionIri, WorkflowState.IN_PROGRESS, WorkflowState.SUCCEEDED);
                if (transitioned) {
                    processSuccess(actionIri);
                }
            } else {
                log.error("[SagaManager] Action {} completed but POST-CONDITIONS FAILED", update.actionId());
                processFailure(actionIri, update.actionId());
            }
        } else {
            log.error("[SagaManager] Action {} failed with status {}", update.actionId(), update.status());
            processFailure(actionIri, update.actionId());
        }
    }

    private void processSuccess(IRI actionIri) {
        // A. Unlock next sibling in the sequence
        log.info("[SagaManager] Looking for steps dependent on {}", actionIri);
        List<IRI> dependents = gateway.findDependents(actionIri);
        
        if (!dependents.isEmpty()) {
            for (IRI dependent : dependents) {
                log.info("[SagaManager] Unlocking dependent step {}", dependent);
                gateway.transitionState(dependent, WorkflowState.INITIAL.getFragment());
            }
        } else {
            // B. If no siblings, check if we need to complete the parent (Join Logic)
            IRI parentIri = gateway.findParent(actionIri);
            if (parentIri != null) {
                log.info("[SagaManager] No more siblings. Checking parent workflow {}", parentIri);
                checkParentCompletion(parentIri);
            }
        }
    }

    private void processFailure(IRI actionIri, String actionId) {
        boolean transitioned = stateRepository.transition(actionIri, WorkflowState.IN_PROGRESS, WorkflowState.FAILED);
        if (transitioned) {
            // 1. Fail parent (recursive)
            IRI parentIri = gateway.findParent(actionIri);
            if (parentIri != null) {
                stateRepository.transition(parentIri, WorkflowState.PLANNED, WorkflowState.FAILED);
            }

            // 2. Trigger Compensation (Rollback)
            IRI compensationIri = gateway.findCompensation(actionIri);
            if (compensationIri != null) {
                log.info("[SagaManager] Triggering compensation {} for action {}", compensationIri, actionId);
                String compId = "comp-" + UUID.randomUUID().toString().substring(0, 8);
                var originalAction = gateway.fetchActionStructure(actionIri);
                if (originalAction != null) {
                    gateway.createActionWorkflow(originalAction.target(), compensationIri, compId);
                    log.info("[SagaManager] Compensation workflow {} created in State_Initial", compId);
                }
            }
        }
    }

    /**
     * Petri Net Join Logic: 
     * Verifies if all children in the decomposition are SUCCEEDED.
     */
    private void checkParentCompletion(IRI parentIri) {
        List<IRI> children = gateway.findChildren(parentIri);
        boolean allSucceeded = true;
        
        for (IRI child : children) {
            WorkflowState childState = gateway.getState(child);
            if (childState != WorkflowState.SUCCEEDED) {
                log.debug("[SagaManager] Parent {} not finished: child {} is in state {}", parentIri, child, childState);
                allSucceeded = false;
                break;
            }
        }

        if (allSucceeded) {
            log.info("[SagaManager] All children finished. Marking parent workflow {} as SUCCEEDED", parentIri);
            boolean transitioned = stateRepository.transition(parentIri, WorkflowState.PLANNED, WorkflowState.SUCCEEDED);
            
            if (transitioned) {
                // Recurse to parent's parent
                IRI grandParent = gateway.findParent(parentIri);
                if (grandParent != null) {
                    processSuccess(parentIri);
                }
            }
        }
    }

    private boolean verifyPostConditions(IRI actionIri) {
        ActionData data = gateway.fetchActionStructure(actionIri);
        if (data == null || data.postConditions().isEmpty()) {
            return true;
        }

        log.info("[SagaManager] Verifying {} post-conditions for action {}", data.postConditions().size(), actionIri);

        for (ActionData.Condition cond : data.postConditions()) {
            Optional<ConditionStrategy> strategy = conditionFactory.getStrategy(cond.type());
            if (strategy.isPresent()) {
                try {
                    if (!strategy.get().evaluate(cond)) {
                        log.warn("[SagaManager] Post-condition {} NOT MET", cond.id());
                        return false;
                    }
                } catch (Exception e) {
                    log.error("[SagaManager] Error evaluating post-condition {}: {}", cond.id(), e.getMessage());
                    return false;
                }
            } else {
                log.error("[SagaManager] No strategy for post-condition type {}", cond.type());
                return false;
            }
        }
        return true;
    }
}
