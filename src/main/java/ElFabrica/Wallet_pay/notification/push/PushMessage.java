package ElFabrica.Wallet_pay.notification.push;

import java.util.UUID;

public record PushMessage(UUID userId, String title, String body) {
}
