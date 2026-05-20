package com.kubiki.palamedes.listener;

import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.palamedes.saga.SagaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ActionStatusListener {
    private static final Logger log = LoggerFactory.getLogger(ActionStatusListener.class);
    private final SagaManager sagaManager;

    public ActionStatusListener(SagaManager sagaManager) {
        this.sagaManager = sagaManager;
    }

    @RabbitListener(queues = RabbitMQConfig.STATUS_QUEUE)
    public void receiveStatus(ActionStatusUpdate message) {
        log.info("Received status update for action {}: {}", message.actionId(), message.status());
        sagaManager.handleFeedback(message);
    }
}
