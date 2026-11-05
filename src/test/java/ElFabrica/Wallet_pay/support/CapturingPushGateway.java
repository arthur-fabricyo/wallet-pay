package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.notification.push.PushGateway;
import ElFabrica.Wallet_pay.notification.push.PushMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CapturingPushGateway implements PushGateway {

    public final List<PushMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(PushMessage message) {
        sent.add(message);
    }
}
