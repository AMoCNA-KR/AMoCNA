package com.kubiki.themis.execution;

import com.kubiki.themis.config.RabbitMQConfig;
import com.kubiki.themis.model.ActionMessage;
import com.kubiki.themis.model.ActionStatusUpdate;
import com.kubiki.themis.model.ExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActionQueueListener {
    private static final Logger log = LoggerFactory.getLogger(ActionQueueListener.class);
    private final List<ProtocolExecutor> executors;
    private final StatusProducer statusProducer;

    public ActionQueueListener(List<ProtocolExecutor> executors, StatusProducer statusProducer) {
        this.executors = executors;
        this.statusProducer = statusProducer;
    }

    @RabbitListener(queues = RabbitMQConfig.ACTION_QUEUE)
    public void receiveAction(ActionMessage message) {
        log.info("Received action from queue: {}", message.actionId());
        
        ProtocolExecutor executor = executors.stream()
                .filter(e -> e.supports(message.protocol()))
                .findFirst()
                .orElse(null);

        if (executor == null) {
            log.error("No executor found for protocol: {}", message.protocol());
            statusProducer.sendUpdate(new ActionStatusUpdate(message.actionId(), ExecutionStatus.FAILED, "No executor for protocol", 0));
            return;
        }

        boolean success = executor.executeStateless(message);
        
        ActionStatusUpdate status = new ActionStatusUpdate(
            message.actionId(),
            success ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED,
            success ? null : "Execution failed",
            success ? message.expectedStatusCode() : 500 // Simplified
        );
        
        statusProducer.sendUpdate(status);
        log.info("Sent status update for action: {}", message.actionId());
    }
}
