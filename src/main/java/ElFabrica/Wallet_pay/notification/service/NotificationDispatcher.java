package ElFabrica.Wallet_pay.notification.service;

import ElFabrica.Wallet_pay.notification.Notification;
import ElFabrica.Wallet_pay.notification.channel.NotificationChannel;
import ElFabrica.Wallet_pay.notification.repository.NotificationDeliveryRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Delivers a notification through every channel the user has enabled, once per (event, user, channel). Deliberately
 * not transactional: each successful delivery is recorded immediately, so when a later channel throws, a retry only
 * redoes the channels that are still pending.
 */
@Service
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;
    private final NotificationPreferenceService preferences;
    private final NotificationDeliveryRepository deliveries;
    private final Clock clock;

    public NotificationDispatcher(
            List<NotificationChannel> channels,
            NotificationPreferenceService preferences,
            NotificationDeliveryRepository deliveries,
            Clock clock
    ) {
        this.channels = channels;
        this.preferences = preferences;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    public void dispatch(Notification notification) {
        for (NotificationChannel channel : channels) {
            if (!preferences.isEnabled(notification.userId(), channel.type())) {
                continue;
            }
            if (deliveries.exists(notification.eventId(), notification.userId(), channel.type())) {
                continue;
            }
            channel.deliver(notification);
            deliveries.markDelivered(notification.eventId(), notification.userId(), channel.type(), clock.instant());
        }
    }
}
