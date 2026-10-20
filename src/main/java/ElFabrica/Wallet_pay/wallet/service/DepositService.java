package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class DepositService {

    private final RetryTemplate balanceRetryTemplate;
    private final CreateDepositUseCase createDepositUseCase;

    public DepositService(RetryTemplate balanceRetryTemplate, CreateDepositUseCase createDepositUseCase) {
        this.balanceRetryTemplate = balanceRetryTemplate;
        this.createDepositUseCase = createDepositUseCase;
    }

    public IdempotentResult<DepositResult> deposit(DepositCommand command) {
        try {
            return balanceRetryTemplate.invoke(() -> createDepositUseCase.deposit(command));
        } catch (OptimisticLockingFailureException exception) {
            throw new BalanceConflictException("Operacao concorrente na carteira, tente novamente");
        }
    }
}
