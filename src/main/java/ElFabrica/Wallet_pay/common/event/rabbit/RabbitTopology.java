package ElFabrica.Wallet_pay.common.event.rabbit;

import ElFabrica.Wallet_pay.common.event.EventHandler;

/** Naming of the RabbitMQ exchange and queues. Only this package knows about them. */
final class RabbitTopology {

    static final String EXCHANGE = "walletpay.events";

    private RabbitTopology() {
    }

    static String queueName(EventHandler handler) {
        return "walletpay." + handler.consumerName() + "." + handler.eventType();
    }

    static String deadLetterQueueName(EventHandler handler) {
        return queueName(handler) + ".dlq";
    }
}
