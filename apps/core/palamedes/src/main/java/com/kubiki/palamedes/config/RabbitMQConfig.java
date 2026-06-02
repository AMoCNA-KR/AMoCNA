package com.kubiki.palamedes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ACTION_QUEUE = "amocna.action.queue";
    public static final String STATUS_QUEUE = "amocna.status.queue";
    public static final String GRAPH_UPDATES_QUEUE = "amocna.graph.updates";
    public static final String EXCHANGE = "amocna.direct.exchange";
    public static final String TOPIC_EXCHANGE = "amocna.topic.exchange";
    public static final String ACTION_ROUTING_KEY = "action";
    public static final String STATUS_ROUTING_KEY = "status";
    public static final String GRAPH_UPDATES_ROUTING_KEY_PATTERN = "graph.updates.*";

    @Bean
    public Queue actionQueue() {
        return new Queue(ACTION_QUEUE);
    }

    @Bean
    public Queue statusQueue() {
        return new Queue(STATUS_QUEUE);
    }

    @Bean
    public Queue graphUpdatesQueue() {
        return new Queue(GRAPH_UPDATES_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE);
    }

    @Bean
    public Binding actionBinding(Queue actionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(actionQueue).to(exchange).with(ACTION_ROUTING_KEY);
    }

    @Bean
    public Binding statusBinding(Queue statusQueue, DirectExchange exchange) {
        return BindingBuilder.bind(statusQueue).to(exchange).with(STATUS_ROUTING_KEY);
    }

    @Bean
    public Binding graphUpdatesBinding(Queue graphUpdatesQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(graphUpdatesQueue).to(topicExchange).with(GRAPH_UPDATES_ROUTING_KEY_PATTERN);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
