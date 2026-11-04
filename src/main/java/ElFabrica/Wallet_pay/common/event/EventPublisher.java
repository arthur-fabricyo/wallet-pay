package ElFabrica.Wallet_pay.common.event;

/** Port for sending events to a broker. Implementations throw a RuntimeException on failure. */
public interface EventPublisher {

    void publish(DomainEvent event);
}
