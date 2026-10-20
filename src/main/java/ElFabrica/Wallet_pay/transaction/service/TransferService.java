package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final RetryTemplate balanceRetryTemplate;
    private final CreateTransferUseCase createTransferUseCase;

    public TransferService(RetryTemplate balanceRetryTemplate, CreateTransferUseCase createTransferUseCase) {
        this.balanceRetryTemplate = balanceRetryTemplate;
        this.createTransferUseCase = createTransferUseCase;
    }

    public TransferResult transfer(TransferCommand command) {
        try {
            return balanceRetryTemplate.invoke(() -> createTransferUseCase.transfer(command));
        } catch (OptimisticLockingFailureException exception) {
            throw new BalanceConflictException("Operacao concorrente na carteira, tente novamente");
        }
    }
}
