package com.kubiki.palamedes.listener;

import com.kubiki.palamedes.analyzer.AnomalyAgent;
import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.common.model.GraphUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
    public void onGraphUpdate(GraphUpdateMessage message) {
        log.info("Graph update received [correlationId={}, resource={}, change={}]",
                message.correlationId(), message.resourceIri(), message.changeKind());

        anomalyAgent.analyze();
    }
}
