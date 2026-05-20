package com.kubiki.metis.notification;

import com.kubiki.common.model.GraphUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes graph-update notifications to the {@code amocna.graph.updates} RabbitMQ queue
 * so that Palamedes (and any other interested consumer) can react to knowledge-base changes.
 *
 * <p>Failures are logged at ERROR level and silently swallowed — a messaging outage
 * must never roll back a successful graph update.
 */
@Component
public class PalamedesNotifier {

    private static final Logger log = LoggerFactory.getLogger(PalamedesNotifier.class);

    public static final String EXCHANGE = "amocna.direct.exchange";
    public static final String ROUTING_KEY = "graph.updates";

    private final RabbitTemplate rabbitTemplate;

    public PalamedesNotifier(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a graph-update notification to RabbitMQ.
     *
     * @param resourceIri   fully-qualified IRI of the affected resource
     * @param ontologyType  fully-qualified CNEEOnt class IRI of the resource
     * @param changeKind    the kind of change (CREATED, UPDATED, STATE_CHANGED, DELETED)
     * @param correlationId correlation identifier from the originating batch
     */
    public void notify(String resourceIri, String ontologyType,
                       String changeKind, String correlationId) {
        GraphUpdateMessage message = new GraphUpdateMessage(
                resourceIri, ontologyType, changeKind, correlationId);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
            log.debug("Published graph update to RabbitMQ [correlationId={}, resourceIri={}, changeKind={}]",
                    correlationId, resourceIri, changeKind);
        } catch (Exception e) {
            log.error("Failed to publish graph update to RabbitMQ [correlationId={}]: {}",
                    correlationId, e.getMessage(), e);
        }
    }
}
