package ElFabrica.Wallet_pay.wallet.dto;

import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.wallet.service.StatementDirection;
import java.time.Instant;
import java.util.UUID;

public record StatementEntryResponseDTO(
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
