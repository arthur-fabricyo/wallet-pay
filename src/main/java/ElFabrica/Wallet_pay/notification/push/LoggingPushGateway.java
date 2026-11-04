package ElFabrica.Wallet_pay.notification.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default stub. The real implementation (Fase 7) should be declared @Primary to take over. */
@Component
public class LoggingPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushGateway.class);

    @Override
    public void send(PushMessage message) {
        log.info("Push (stub) to user {}: {} - {}", message.userId(), message.title(), message.body());
    }
}
