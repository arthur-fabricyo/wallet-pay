package ElFabrica.Wallet_pay.notification.channel;

import ElFabrica.Wallet_pay.notification.Notification;
import ElFabrica.Wallet_pay.notification.NotificationChannelType;
import ElFabrica.Wallet_pay.notification.push.PushGateway;
import ElFabrica.Wallet_pay.notification.push.PushMessage;
import org.springframework.stereotype.Component;

@Component
public class PushChannel implements NotificationChannel {

    private final PushGateway pushGateway;

    public PushChannel(PushGateway pushGateway) {
        this.pushGateway = pushGateway;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public void deliver(Notification notification) {
        pushGateway.send(new PushMessage(notification.userId(), notification.title(), notification.body()));
    }
}
