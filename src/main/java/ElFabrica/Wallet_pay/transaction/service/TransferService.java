package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import ElFabrica.Wallet_pay.wallet.service.LimitExceededException;
import java.util.Map;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final RetryTemplate balanceRetryTemplate;
    private final CreateTransferUseCase createTransferUseCase;
    private final AuditLogger auditLogger;

    public TransferService(
            RetryTemplate balanceRetryTemplate,
            CreateTransferUseCase createTransferUseCase,
            AuditLogger auditLogger
    ) {
        this.balanceRetryTemplate = balanceRetryTemplate;
        this.createTransferUseCase = createTransferUseCase;
        this.auditLogger = auditLogger;
    }

    public IdempotentResult<TransferResult> transfer(TransferCommand command) {
        try {
            return transferWithRetry(command);
        } catch (BusinessException exception) {
            auditLogger.recordFailure(AuditEntry.failure(
                    AuditAction.TRANSFER_REJECTED,
                    null,
                    exception.getErrorCode(),
                    rejectionMetadata(command, exception)
            ).withActor(command.senderUserId()));
            throw exception;
        }
    }

    private IdempotentResult<TransferResult> transferWithRetry(TransferCommand command) {
        try {
            return balanceRetryTemplate.invoke(() -> createTransferUseCase.transfer(command));
        } catch (OptimisticLockingFailureException exception) {
            throw new BalanceConflictException("Operacao concorrente na carteira, tente novamente");
        }
    }

    private static Map<String, String> rejectionMetadata(TransferCommand command, BusinessException exception) {
        return AuditEntry.metadata(
                "amount", AuditEntry.truncateRaw(command.amount()),
                "receiverUserId", command.receiverUserId(),
                "failureCode", exception.getErrorCode(),
                "limitType", exception instanceof LimitExceededException limitExceeded ? limitExceeded.getLimitType() : null
        );
    }
}
