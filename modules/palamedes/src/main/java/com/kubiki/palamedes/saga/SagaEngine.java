package com.kubiki.palamedes.saga;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ExecutionStatus;
import com.kubiki.palamedes.dispatcher.DispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SagaEngine {
    private static final Logger log = LoggerFactory.getLogger(SagaEngine.class);
    private final GraphDBGateway gateway;
    private final ExecutorService executor;

    public SagaEngine(GraphDBGateway gateway) {
        this.gateway = gateway;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SagaEngine executor");
        executor.shutdown();
    }

    public void handleFeedback(com.kubiki.palamedes.model.ActionStatusUpdate update) {
        log.info("Handling feedback for action {}: {}", update.actionId(), update.status());
        // TODO: Implement Saga state machine logic
        // If SUCCESS and part of workflow, dispatch next step
        // If FAILURE, trigger compensation
    }

    // Deprecated/Placeholder methods for compilation
    public boolean execute(ActionData action, UUID executionId, DispatcherService dispatcher) {
        log.warn("Blocking execution is deprecated in the async MAPE split");
        return false;
    }
}
