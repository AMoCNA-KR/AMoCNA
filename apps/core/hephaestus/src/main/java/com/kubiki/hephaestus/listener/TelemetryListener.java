package com.kubiki.hephaestus.listener;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.hephaestus.config.RabbitMQConfig;
import com.kubiki.hephaestus.model.TelemetryEvent;
import com.kubiki.hephaestus.service.EventStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for telemetry messages on the Hephaestus RabbitMQ queue
 * and relays them directly to the active event stream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitMQConfig.TELEMETRY_QUEUE)
public class TelemetryListener {

    private final EventStreamService eventStreamService;

    @RabbitHandler
    public void handleAction(ActionMessage message) {
        log.info("Telemetry captured: Action message received [id={}]", message.actionId());
        eventStreamService.publish(new TelemetryEvent("action", message));
    }

    @RabbitHandler
    public void handleStatus(ActionStatusUpdate message) {
        log.info("Telemetry captured: Action status update [id={}, status={}]", message.actionId(), message.status());
        eventStreamService.publish(new TelemetryEvent("status", message));
    }

    @RabbitHandler
    public void handleGraphUpdate(GraphUpdateMessage message) {
        log.info("Telemetry captured: Graph update [resource={}, change={}]", message.resourceIri(), message.changeKind());
        eventStreamService.publish(new TelemetryEvent("graph.updates", message));
    }
}
