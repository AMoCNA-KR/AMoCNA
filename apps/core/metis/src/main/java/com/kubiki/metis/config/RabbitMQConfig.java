package com.kubiki.metis.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure for Metis → Palamedes graph-update notifications.
 *
 * <p>Declares the {@code amocna.graph.updates} queue bound to the shared
 * {@code amocna.direct.exchange} with routing key {@code graph.updates}.
 * Palamedes (or any other consumer) listens on this queue.
 */
@Configuration
public class RabbitMQConfig {

    public static final String GRAPH_UPDATES_QUEUE = "amocna.graph.updates";
    public static final String EXCHANGE = "amocna.direct.exchange";
    public static final String ROUTING_KEY = "graph.updates";

    @Bean
    public Queue graphUpdatesQueue() {
        return new Queue(GRAPH_UPDATES_QUEUE, true);
    }

    @Bean
    public DirectExchange amocnaExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Binding graphUpdatesBinding(Queue graphUpdatesQueue, DirectExchange amocnaExchange) {
        return BindingBuilder.bind(graphUpdatesQueue).to(amocnaExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
