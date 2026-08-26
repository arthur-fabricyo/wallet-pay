package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record StatementEntryResult(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        StatementDirection direction,
        String amount,
        String currency,
        String counterpartyName,
        String description,
        Instant createdAt,
        Instant completedAt
) {
}
