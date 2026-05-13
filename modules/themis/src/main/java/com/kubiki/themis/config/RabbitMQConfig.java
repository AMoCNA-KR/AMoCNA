package com.kubiki.themis.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ACTION_QUEUE = "amocna.action.queue";
    public static final String STATUS_QUEUE = "amocna.status.queue";
    public static final String EXCHANGE = "amocna.direct.exchange";

    @Bean
    public Queue actionQueue() {
        return new Queue(ACTION_QUEUE);
    }

    @Bean
    public Queue statusQueue() {
        return new Queue(STATUS_QUEUE);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Binding actionBinding(Queue actionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(actionQueue).to(exchange).with("action");
    }

    @Bean
    public Binding statusBinding(Queue statusQueue, DirectExchange exchange) {
        return BindingBuilder.bind(statusQueue).to(exchange).with("status");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
