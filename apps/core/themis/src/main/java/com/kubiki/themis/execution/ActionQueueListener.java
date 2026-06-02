package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.themis.config.RabbitMQConfig;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ExecutionResult;
import com.kubiki.themis.policy.ConditionEvaluator;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kubiki.common.logging.MdcContext;
import com.kubiki.common.logging.MdcParam;
import com.kubiki.common.logging.PreVerify;
import com.kubiki.common.logging.PostVerify;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActionQueueListener {
    private static final Logger log = LoggerFactory.getLogger(ActionQueueListener.class);
    private final List<ProtocolExecutor> executors;

    @PreVerify
    @PostVerify
    @MdcContext
    @RabbitListener(queues = RabbitMQConfig.ACTION_QUEUE)
    @Timed(value = "themis.queue.receive", description = "Time taken to process action from queue")
    public ExecutionResult receiveAction(
            @MdcParam(value = "actionId", property = "actionId")
            @MdcParam(value = "protocol", property = "protocol") ActionMessage message) {
        log.info("Received action from queue: {}", message.actionId());

        ProtocolExecutor executor = executors.stream()
                .filter(e -> e.supports(message.protocol()))
                .findFirst()
                .orElse(null);

        if (executor == null) {
            log.error("No executor found for protocol: {}", message.protocol());
            return ExecutionResult.failure(0, "No executor for protocol", ExecutionStatus.FAILED_INTERNAL);
        }

        return executeWithRetry(executor, message);
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
