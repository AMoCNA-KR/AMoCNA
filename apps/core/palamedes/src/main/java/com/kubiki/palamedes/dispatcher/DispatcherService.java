package com.kubiki.palamedes.dispatcher;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.model.ActionMessage;
import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;


@Service
public class DispatcherService {
    private static final Logger log = LoggerFactory.getLogger(DispatcherService.class);
    private final RabbitTemplate rabbitTemplate;
    private final GraphDBGateway graphDBGateway;

    public DispatcherService(RabbitTemplate rabbitTemplate, GraphDBGateway graphDBGateway) {
        this.rabbitTemplate = rabbitTemplate;
        this.graphDBGateway = graphDBGateway;
    }

    @LogLoopStep(
        phase = LoopPhase.DISPATCH,
        step = "Dispatched Action to Queue",
        actionId = "#message.actionId()",
        resource = "#message.resourceName()",
        details = "'protocol=' + #message.protocol() + ', maxRetries=' + #message.maxRetries()"
    )
    public void dispatch(ActionMessage message) {
        // Update GraphDB: Set current state to State_InProgress
        // graphDBGateway.updateActionStatus(message.actionId(), "State_InProgress"); 

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ACTION_ROUTING_KEY, message);
    }
}
