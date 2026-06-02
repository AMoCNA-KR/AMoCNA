package com.kubiki.palamedes.listener;

import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.palamedes.analyzer.AnomalyAgent;
import com.kubiki.palamedes.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens for graph-update notifications from Metis on the
 * {@code amocna.graph.updates} RabbitMQ queue and triggers the
 * anomaly analysis pipeline immediately.
 *
 * <p>This makes Palamedes event-driven: instead of polling GraphDB every N seconds,
 * it reacts within milliseconds of a knowledge-base change.
 */
@Component
public class GraphUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(GraphUpdateListener.class);

    private final AnomalyAgent anomalyAgent;

    public GraphUpdateListener(AnomalyAgent anomalyAgent) {
        this.anomalyAgent = anomalyAgent;
    }

    @RabbitListener(queues = RabbitMQConfig.GRAPH_UPDATES_QUEUE)
    public void onGraphUpdate(GraphUpdateMessage message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        MDC.put("correlationId", message.correlationId());
        MDC.put("routingKey", routingKey);
        MDC.put("resourceIri", message.resourceIri());
        MDC.put("changeKind", message.changeKind());
        MDC.put("ontologyType", message.ontologyType());

        try {
            if (ThreadLocalRandom.current().nextDouble() < 0.10) {
                log.info("GraphUpdateListener.onGraphUpdate received message from routing key {} [correlationId={}, resource={}, change={}]",
                        routingKey, message.correlationId(), message.resourceIri(), message.changeKind());
            } else {
                log.debug("GraphUpdateListener.onGraphUpdate received message from routing key {} [correlationId={}, resource={}, change={}]",
                        routingKey, message.correlationId(), message.resourceIri(), message.changeKind());
            }

            log.info("GraphUpdateListener: Routing update, triggering AnomalyAgent.analyze()");
            anomalyAgent.analyze();

            log.info("GraphUpdateListener: Execution finished for correlationId={}", message.correlationId());
        } finally {
            MDC.clear();
        }
    }
}
