package ElFabrica.Wallet_pay.common.event.rabbit;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.common.event.EventPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** RabbitMQ implementation of {@link EventPublisher}: topic exchange, routing key = event type, plain JSON body. */
@Component
public class RabbitEventPublisher implements EventPublisher {

    static final String EVENT_ID_HEADER = "eventId";
    static final String REQUEST_ID_HEADER = "requestId";

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final RabbitTopology topology;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate, JsonMapper jsonMapper, RabbitTopology topology) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
        this.topology = topology;
    }

    @Override
    public void publish(DomainEvent event) {
        MessageBuilderSupport<Message> builder = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.eventId().toString())
                .setHeader(EVENT_ID_HEADER, event.eventId().toString());
        if (event.requestId() != null) {
            builder.setHeader(REQUEST_ID_HEADER, event.requestId());
        }
        Message message = builder.build();

        rabbitTemplate.send(topology.exchange(), event.type(), message);
    }
}
