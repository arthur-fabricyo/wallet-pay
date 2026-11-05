package ElFabrica.Wallet_pay.notification.channel;

import ElFabrica.Wallet_pay.notification.Notification;
import ElFabrica.Wallet_pay.notification.NotificationChannelType;

/** A way of reaching a user (push, e-mail, ...). Throwing from {@link #deliver} means the delivery failed. */
public interface NotificationChannel {

    NotificationChannelType type();

    void deliver(Notification notification);
}
