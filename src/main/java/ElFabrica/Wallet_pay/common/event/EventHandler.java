package ElFabrica.Wallet_pay.common.event;

/** Consumes one event type. Throwing means the delivery failed and will be retried (then dead-lettered). */
public interface EventHandler {

    String consumerName();

    String eventType();

    void handle(DomainEvent event);
}
