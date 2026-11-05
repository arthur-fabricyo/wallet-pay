package ElFabrica.Wallet_pay.notification;

import java.util.UUID;

/** A channel-independent message for one user, produced from one domain event. */
public record Notification(UUID eventId, UUID userId, String kind, String title, String body) {

    public static final String TRANSFER_SENT = "TRANSFER_SENT";
    public static final String TRANSFER_RECEIVED = "TRANSFER_RECEIVED";
}
