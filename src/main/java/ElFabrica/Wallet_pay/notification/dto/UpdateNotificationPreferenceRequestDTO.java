package ElFabrica.Wallet_pay.notification.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequestDTO(
        @NotNull(message = "enabled e obrigatorio")
        Boolean enabled
) {
}
