package ElFabrica.Wallet_pay.transaction.service;

import java.util.UUID;

public record TransferCommand(
        UUID senderUserId,
        UUID receiverUserId,
        String amount,
        String description,
        String idempotencyKey
) {
}
