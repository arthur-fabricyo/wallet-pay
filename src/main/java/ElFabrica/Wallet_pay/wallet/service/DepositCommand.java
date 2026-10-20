package ElFabrica.Wallet_pay.wallet.service;

import java.util.UUID;

public record DepositCommand(
        UUID userId,
        String amount,
        String description,
        String idempotencyKey
) {
}
