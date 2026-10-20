package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record DepositResult(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        String amount,
        String currency,
        String description,
        DepositWalletResult wallet,
        Instant createdAt,
        Instant completedAt
) {
}
