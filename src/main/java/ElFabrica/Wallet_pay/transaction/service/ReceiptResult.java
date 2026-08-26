package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResult(
        UUID receiptId,
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        String amount,
        String currency,
        String senderName,
        String senderDocumentMasked,
        String receiverName,
        String receiverDocumentMasked,
        String description,
        Instant createdAt,
        Instant completedAt
) {
}
