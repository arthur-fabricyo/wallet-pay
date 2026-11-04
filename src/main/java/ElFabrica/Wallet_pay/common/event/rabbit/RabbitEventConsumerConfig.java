package ElFabrica.Wallet_pay.common.event.rabbit;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.common.event.EventHandler;
import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares exchange/queues/DLQs and one listener per {@link EventHandler} bean. Failed handling is retried by the
 * listener container (see spring.rabbitmq.listener.simple.retry.*) and then dead-lettered.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitEventConsumerConfig implements RabbitListenerConfigurer {

    private final List<EventHandler> handlers;
    private final JsonMapper jsonMapper;

    public RabbitEventConsumerConfig(List<EventHandler> handlers, JsonMapper jsonMapper) {
        this.handlers = handlers;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    TopicExchange walletPayEventsExchange() {
        return new TopicExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    Declarables eventHandlerQueues(TopicExchange walletPayEventsExchange) {
        List<Declarable> declarables = new ArrayList<>();
        for (EventHandler handler : handlers) {
            String queueName = RabbitTopology.queueName(handler);
            String dlqName = RabbitTopology.deadLetterQueueName(handler);

            Queue dlq = QueueBuilder.durable(dlqName).build();
            Queue queue = QueueBuilder.durable(queueName)
                    .deadLetterExchange("")
                    .deadLetterRoutingKey(dlqName)
                    .build();
            Binding binding = BindingBuilder.bind(queue).to(walletPayEventsExchange).with(handler.eventType());

            declarables.add(dlq);
            declarables.add(queue);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        for (EventHandler handler : handlers) {
            SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
            endpoint.setId("event-handler-" + RabbitTopology.queueName(handler));
            endpoint.setQueueNames(RabbitTopology.queueName(handler));
            endpoint.setMessageListener(listenerFor(handler));
            registrar.registerEndpoint(endpoint);
        }
    }

    private MessageListener listenerFor(EventHandler handler) {
        return (Message message) -> {
            DomainEvent event;
            try {
                event = jsonMapper.readValue(message.getBody(), DomainEvent.class);
            } catch (RuntimeException e) {
                // A malformed message will never become valid: skip retries, go straight to the DLQ.
                throw new AmqpRejectAndDontRequeueException("Unreadable event message", e);
            }
            String previous = MDC.get(RequestIdFilter.MDC_KEY);
            if (event.requestId() != null) {
                MDC.put(RequestIdFilter.MDC_KEY, event.requestId());
            }
            try {
                handler.handle(event);
            } finally {
                if (previous != null) {
                    MDC.put(RequestIdFilter.MDC_KEY, previous);
                } else {
                    MDC.remove(RequestIdFilter.MDC_KEY);
                }
            }
        };
    }
}
