package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.themis.config.RabbitMQConfig;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ExecutionResult;
import com.kubiki.themis.policy.ConditionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActionQueueListener {
    private static final Logger log = LoggerFactory.getLogger(ActionQueueListener.class);
    private final List<ProtocolExecutor> executors;
    private final StatusProducer statusProducer;
    private final ConditionEvaluator conditionEvaluator;
    private final ThemisProperties properties;

    public ActionQueueListener(List<ProtocolExecutor> executors, StatusProducer statusProducer, ConditionEvaluator conditionEvaluator, ThemisProperties properties) {
        this.executors = executors;
        this.statusProducer = statusProducer;
        this.conditionEvaluator = conditionEvaluator;
        this.properties = properties;
    }

    @RabbitListener(queues = RabbitMQConfig.ACTION_QUEUE)
    public void receiveAction(ActionMessage message) {
        log.info("Received action from queue: {}", message.actionId());

        if (!conditionEvaluator.evaluatePreConditions(message.actionId())) {
            log.warn("Pre-conditions failed for action: {}", message.actionId());
            statusProducer.sendUpdate(new ActionStatusUpdate(message.actionId(), ExecutionStatus.FAILED_INTERNAL, "Pre-condition failed", 0));
            return;
        }

        ProtocolExecutor executor = executors.stream()
                .filter(e -> e.supports(message.protocol()))
                .findFirst()
                .orElse(null);

        if (executor == null) {
            log.error("No executor found for protocol: {}", message.protocol());
            statusProducer.sendUpdate(new ActionStatusUpdate(message.actionId(), ExecutionStatus.FAILED_INTERNAL, "No executor for protocol", 0));
            return;
        }

        ExecutionResult result = executeWithRetry(executor, message);

        if (result.success()) {
            int delay = properties.execution().postConditionDelayMs();
            if (delay > 0) {
                try {
                    log.info("Waiting {}ms for cluster stabilization before post-condition verification", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Stabilization delay interrupted", e);
                }
            }

            if (!conditionEvaluator.evaluatePostConditions(message.actionId())) {
                log.warn("Post-condition verification failed for action: {}", message.actionId());
                result = ExecutionResult.failure(result.observedStatusCode(), "Post-condition verification failed", ExecutionStatus.FAILED_INTERNAL);
            }
        }

        ActionStatusUpdate status = new ActionStatusUpdate(
                message.actionId(),
                result.status(),
                result.errorMessage(),
                result.observedStatusCode()
        );

        statusProducer.sendUpdate(status);
        log.info("Sent status update for action {}: {}", message.actionId(), result.status());
    }

    private ExecutionResult executeWithRetry(ProtocolExecutor executor, ActionMessage message) {
        int maxAttempts = message.isIdempotent() ? Math.max(1, message.maxRetries() + 1) : 1;

        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .fixedBackoff(1000) // 1 second backoff
                .retryOn(RuntimeException.class) // Broad retry, refined by logic below if needed
                .build();

        try {
            return retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("Retry attempt {} for action {}", context.getRetryCount(), message.actionId());
                }
                ExecutionResult result = executor.executeStateless(message);

                // If failed but not a retryable error (e.g. 400 Bad Request), we might want to stop retrying.
                // However, the MoaMont 'isIdempotent' flag usually implies we CAN retry on network/timeout issues.
                // For now, if result is not success, we throw an exception to trigger retry IF it's a retryable status.
                if (!result.success() && isRetryable(result.status()) && context.getRetryCount() < maxAttempts - 1) {
                    throw new RuntimeException("Execution failed, triggering retry: " + result.errorMessage());
                }
                return result;
            });
        } catch (Exception e) {
            log.error("Execution failed after all retries for action {}: {}", message.actionId(), e.getMessage());
            // This catch handles the case where the last attempt also failed and threw the exception
            return ExecutionResult.failure(500, e.getMessage(), ExecutionStatus.FAILED_INTERNAL);
        }
    }

    private boolean isRetryable(ExecutionStatus status) {
        return status == ExecutionStatus.FAILED_TIMEOUT || status == ExecutionStatus.FAILED_INTERNAL;
    }
}
