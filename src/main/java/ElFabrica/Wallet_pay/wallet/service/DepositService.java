package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import java.util.Map;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class DepositService {

    private final RetryTemplate balanceRetryTemplate;
    private final CreateDepositUseCase createDepositUseCase;
    private final AuditLogger auditLogger;

    public DepositService(
            RetryTemplate balanceRetryTemplate,
            CreateDepositUseCase createDepositUseCase,
            AuditLogger auditLogger
    ) {
        this.balanceRetryTemplate = balanceRetryTemplate;
        this.createDepositUseCase = createDepositUseCase;
        this.auditLogger = auditLogger;
    }

    public IdempotentResult<DepositResult> deposit(DepositCommand command) {
        try {
            return depositWithRetry(command);
        } catch (BusinessException exception) {
            auditLogger.recordFailure(AuditEntry.failure(
                    AuditAction.DEPOSIT_REJECTED,
                    null,
                    exception.getErrorCode(),
                    rejectionMetadata(command, exception)
            ).withActor(command.userId()));
            throw exception;
        }
    }

    private IdempotentResult<DepositResult> depositWithRetry(DepositCommand command) {
        try {
            return balanceRetryTemplate.invoke(() -> createDepositUseCase.deposit(command));
        } catch (OptimisticLockingFailureException exception) {
            throw new BalanceConflictException("Operacao concorrente na carteira, tente novamente");
        }
    }

    private static Map<String, String> rejectionMetadata(DepositCommand command, BusinessException exception) {
        return AuditEntry.metadata(
                "amount", command.amount(),
                "failureCode", exception.getErrorCode()
        );
    }
}
