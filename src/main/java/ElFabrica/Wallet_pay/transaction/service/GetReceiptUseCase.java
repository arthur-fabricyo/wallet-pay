package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetReceiptUseCase {

    private final TransactionRepository transactionRepository;

    public GetReceiptUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public ReceiptResult getReceipt(UUID authenticatedUserId, UUID transactionId) {
        TransactionEntity transaction = transactionRepository.findReceiptById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transacao nao encontrada"));

        UserEntity sender = transaction.getSenderWallet() == null ? null : transaction.getSenderWallet().getUser();
        UserEntity receiver = transaction.getReceiverWallet().getUser();
        boolean participates = (sender != null && sender.getId().equals(authenticatedUserId))
                || receiver.getId().equals(authenticatedUserId);

        if (!participates) {
            throw new ReceiptAccessDeniedException("Usuario nao participa da transacao");
        }

        if (transaction.getType() != TransactionType.TRANSFER) {
            throw new ReceiptUnavailableException("Comprovante disponivel apenas para transferencias");
        }

        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ReceiptUnavailableException("Comprovante disponivel apenas para transacoes concluidas");
        }

        return new ReceiptResult(
                transaction.getId(),
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                Money.format(transaction.getAmount()),
                transaction.getCurrency(),
                sender.getName(),
                maskDocument(sender.getDocument()),
                receiver.getName(),
                maskDocument(receiver.getDocument()),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

    private String maskDocument(String document) {
        if (document == null || document.isBlank()) {
            return "";
        }

        String digits = document.replaceAll("\\D", "");
        if (digits.length() <= 6) {
            return "*".repeat(digits.length());
        }

        int visibleStart = digits.length() == 11 ? 3 : Math.max(0, (digits.length() - 6) / 2);
        int visibleEnd = visibleStart + 6;
        return "*".repeat(visibleStart)
                + digits.substring(visibleStart, visibleEnd)
                + "*".repeat(digits.length() - visibleEnd);
    }
}
