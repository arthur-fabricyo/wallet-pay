package ElFabrica.Wallet_pay.wallet.dto;

import java.time.Instant;
import java.util.UUID;

public record DepositWalletResponseDTO(
        UUID id,
        String balance,
        String currency,
        Instant updatedAt
) {
}
