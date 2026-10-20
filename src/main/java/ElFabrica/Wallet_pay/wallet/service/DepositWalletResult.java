package ElFabrica.Wallet_pay.wallet.service;

import java.time.Instant;
import java.util.UUID;

public record DepositWalletResult(
        UUID id,
        String balance,
        String currency,
        Instant updatedAt
) {
}
