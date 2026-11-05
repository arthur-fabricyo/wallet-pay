package ElFabrica.Wallet_pay.common.event.rabbit;

import ElFabrica.Wallet_pay.common.event.EventHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Naming of the RabbitMQ exchange and queues. Only this package knows about them. The optional
 * walletpay.events.namespace prefixes every name so independent application instances (or test contexts) sharing
 * one broker do not compete for each other's messages; it is empty in normal deployments.
 */
@Component
class RabbitTopology {

    private final String prefix;

    RabbitTopology(@Value("${walletpay.events.namespace:}") String namespace) {
        this.prefix = namespace == null || namespace.isBlank() ? "" : namespace + ".";
    }

    String exchange() {
        return prefix + "walletpay.events";
    }

    String queueName(String consumerName, String eventType) {
        return prefix + "walletpay." + consumerName + "." + eventType;
    }

    String deadLetterQueueName(String consumerName, String eventType) {
        return queueName(consumerName, eventType) + ".dlq";
    }

    String queueName(EventHandler handler) {
        return queueName(handler.consumerName(), handler.eventType());
    }

    String deadLetterQueueName(EventHandler handler) {
        return deadLetterQueueName(handler.consumerName(), handler.eventType());
    }
}
