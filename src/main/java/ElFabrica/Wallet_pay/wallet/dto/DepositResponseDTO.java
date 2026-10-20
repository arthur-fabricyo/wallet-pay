package ElFabrica.Wallet_pay.wallet.dto;

import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record DepositResponseDTO(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        String amount,
        String currency,
        String description,
        DepositWalletResponseDTO wallet,
        Instant createdAt,
        Instant completedAt
) {
}
