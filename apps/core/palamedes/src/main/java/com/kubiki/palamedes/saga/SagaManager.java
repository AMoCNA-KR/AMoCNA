package com.kubiki.palamedes.saga;

import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.EngineWakeupEvent;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * SagaManager (MAPE-Monitor/Analyze):
 * Handles execution feedback from Themis and manages workflow state/compensations.
 * Evaluates Post-conditions for verification.
 */
@Service
@RequiredArgsConstructor
public class SagaManager {
    private static final Logger log = LoggerFactory.getLogger(SagaManager.class);
    private final ActionUtils utils;
    private final GraphDBGateway gateway;
    private final StateRepository stateRepository;
    private final OntologyRegistry ontologyRegistry;
    private final ConditionFactory conditionFactory;
    private final WorkflowStateMapper mapper;
    private final ApplicationEventPublisher publisher;


    public void handleFeedback(ActionStatusUpdate update) {
        log.info("SagaManager: Handling feedback");

        IRI actionIri = ontologyRegistry.actionsOntology(update.actionId());

        if (update.status() == ExecutionStatus.COMPLETED) {
            // 1. VERIFICATION: Evaluate Post-conditions
            log.info("SagaManager: Action completed. Evaluating post-conditions...");
            if (verifyPostConditions(actionIri)) {
                log.info("SagaManager: Action succeeded and verified");

                // 2. DEFENSE IN DEPTH: Clear the anomaly state from the targeted resource
                ActionData data = gateway.fetchActionStructure(actionIri);
                if (data != null && data.target() != null) {
                    log.info("SagaManager: Clearing anomaly state from resource target: {}", data.target());
                    gateway.clearResourceState(data.target());
                }

                log.info("SagaManager: Transitioning action from State_InProgress to State_Succeeded");
                boolean transitioned = stateRepository.transition(actionIri, WorkflowState.IN_PROGRESS, WorkflowState.SUCCEEDED);
                if (transitioned) {
                    log.info("SagaManager: Successfully transitioned action to State_Succeeded. Processing success cascades.");
                    processSuccess(actionIri);
                } else {
                    log.error("SagaManager: Failed to transition action to State_Succeeded");
                }
            } else {
                log.error("SagaManager: Action completed but POST-CONDITIONS FAILED");
                processFailure(actionIri);
            }
        } else {
            log.error("SagaManager: Action failed");
            processFailure(actionIri);
        }

        log.info("SagaManager: Publishing EngineWakeupEvent after feedback update");
        publisher.publishEvent(new EngineWakeupEvent("Saga state updated from Themis feedback"));
    }

    private void processSuccess(IRI actionIri) {
        // A. Unlock next sibling in the sequence
        log.info("Looking for steps dependent on {}", actionIri);
        List<IRI> dependents = gateway.findDependents(actionIri);

        if (!dependents.isEmpty()) {
            for (IRI dependent : dependents) {
                log.info("Unlocking dependent step {}", dependent);
                gateway.transitionState(dependent, mapper.getFragment(WorkflowState.INITIAL));
            }
        } else {
            // B. If no siblings, check if we need to complete the parent (Join Logic)
            IRI parentIri = gateway.findParent(actionIri);
            if (parentIri != null) {
                log.info("No more siblings. Checking parent workflow {}", parentIri);
                checkParentCompletion(parentIri);
            }
        }
    }

    private void processFailure(IRI actionIri) {
        boolean transitioned = stateRepository.transition(actionIri, WorkflowState.IN_PROGRESS, WorkflowState.FAILED);
        if (transitioned) {
            // 1. Mark parent as COMPENSATING
            IRI parentIri = gateway.findParent(actionIri);
            if (parentIri != null) {
                stateRepository.transition(parentIri, WorkflowState.PLANNED, WorkflowState.COMPENSATING);
                stateRepository.transition(parentIri, WorkflowState.IN_PROGRESS, WorkflowState.COMPENSATING);

                // Rollback all completed siblings
                List<IRI> siblings = gateway.findChildren(parentIri);
                for (IRI sibling : siblings) {
                    if (gateway.getState(sibling) == WorkflowState.SUCCEEDED) {
                        triggerCompensation(sibling);
                    }
                }
            }

            // 2. Trigger Compensation for the failed action itself (if applicable)
            triggerCompensation(actionIri);
        }
    }

    private void triggerCompensation(IRI actionIri) {
        IRI compensationIri = gateway.findCompensation(actionIri);
        if (compensationIri != null) {
            log.info("Triggering compensation {} for action {}", compensationIri, actionIri);
            String compId = utils.generateCompensationId();
            ActionData originalAction = gateway.fetchActionStructure(actionIri);
            if (originalAction != null) {
                gateway.createActionWorkflow(originalAction.target(), compensationIri, compId);
                log.info("Compensation workflow {} created in State_Initial", compId);
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
                log.debug("Parent {} not finished: child {} is in state {}", parentIri, child, childState);
                allSucceeded = false;
                break;
            }
        }

        if (allSucceeded) {
            log.info("All children finished. Marking parent workflow {} as SUCCEEDED", parentIri);
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

        log.info("Verifying {} post-conditions for action {}", data.postConditions().size(), actionIri);

        for (ActionData.Condition cond : data.postConditions()) {
            Optional<ConditionStrategy> strategy = conditionFactory.getStrategy(cond.type());
            if (strategy.isPresent()) {
                try {
                    if (!strategy.get().evaluate(cond)) {
                        log.warn("Post-condition {} NOT MET", cond.id());
                        return false;
                    }
                } catch (Exception e) {
                    log.error("Error evaluating post-condition {}: {}", cond.id(), e.getMessage());
                    return false;
                }
            } else {
                log.error("No strategy for post-condition type {}", cond.type());
                return false;
            }
        }
        return true;
    }
}
