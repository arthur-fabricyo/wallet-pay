package ElFabrica.Wallet_pay.notification.dto;

import ElFabrica.Wallet_pay.notification.NotificationChannelType;

public record NotificationPreferenceResponseDTO(NotificationChannelType channel, boolean enabled) {
}
