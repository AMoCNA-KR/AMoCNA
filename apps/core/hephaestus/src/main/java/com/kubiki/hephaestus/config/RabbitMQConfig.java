package com.kubiki.hephaestus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RabbitMQConfig {

    public static final String TELEMETRY_QUEUE = "amocna.hephaestus.telemetry";
    public static final String EXCHANGE = "amocna.direct.exchange";
    
    public static final String ROUTING_KEY_ACTION = "action";
    public static final String ROUTING_KEY_STATUS = "status";
    public static final String ROUTING_KEY_GRAPH_UPDATES = "graph.updates";

    @Bean
    public Queue telemetryQueue() {
        // Create an auto-delete, non-durable queue so that when Hephaestus restarts,
        // it doesn't leave orphaned queues or accumulate message backlogs in RabbitMQ.
        return new Queue(TELEMETRY_QUEUE, false, false, true);
    }

    @Bean
    public DirectExchange amocnaExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Binding actionBinding(Queue telemetryQueue, DirectExchange amocnaExchange) {
        return BindingBuilder.bind(telemetryQueue).to(amocnaExchange).with(ROUTING_KEY_ACTION);
    }

    @Bean
    public Binding statusBinding(Queue telemetryQueue, DirectExchange amocnaExchange) {
        return BindingBuilder.bind(telemetryQueue).to(amocnaExchange).with(ROUTING_KEY_STATUS);
    }

    @Bean
    public Binding graphUpdatesBinding(Queue telemetryQueue, DirectExchange amocnaExchange) {
        return BindingBuilder.bind(telemetryQueue).to(amocnaExchange).with(ROUTING_KEY_GRAPH_UPDATES);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
