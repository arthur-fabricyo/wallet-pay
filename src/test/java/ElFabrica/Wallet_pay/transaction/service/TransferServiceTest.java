package ElFabrica.Wallet_pay.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.config.ResilienceConfig;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class TransferServiceTest {

    private static final TransferCommand COMMAND = new TransferCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "10.00",
            null,
            "chave-1"
    );

    @Test
    void shouldRetryOptimisticLockingFailuresUntilSuccess() {
        FakeCreateTransferUseCase useCase = new FakeCreateTransferUseCase(2);
        TransferService service = new TransferService(new ResilienceConfig().balanceRetryTemplate(), useCase);

        IdempotentResult<TransferResult> outcome = service.transfer(COMMAND);

        assertThat(outcome.result()).isSameAs(useCase.result);
        assertThat(useCase.calls).isEqualTo(3);
    }

    @Test
    void shouldGiveUpAfterFourCallsWithBalanceConflict() {
        FakeCreateTransferUseCase useCase = new FakeCreateTransferUseCase(Integer.MAX_VALUE);
        TransferService service = new TransferService(new ResilienceConfig().balanceRetryTemplate(), useCase);

        assertThatThrownBy(() -> service.transfer(COMMAND))
                .isInstanceOf(BalanceConflictException.class)
                .hasMessage("Operacao concorrente na carteira, tente novamente");
        assertThat(useCase.calls).isEqualTo(4);
    }

    @Test
    void shouldNotRetryBusinessErrors() {
        FakeCreateTransferUseCase useCase = new FakeCreateTransferUseCase(0);
        useCase.businessError = new InsufficientBalanceException("Saldo insuficiente");
        TransferService service = new TransferService(new ResilienceConfig().balanceRetryTemplate(), useCase);

        assertThatThrownBy(() -> service.transfer(COMMAND))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(useCase.calls).isEqualTo(1);
    }

    static class FakeCreateTransferUseCase extends CreateTransferUseCase {

        private final int failuresBeforeSuccess;
        private final TransferResult result = new TransferResult(
                UUID.randomUUID(),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                "10.00",
                "BRL",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                Instant.now(),
                Instant.now()
        );
        private RuntimeException businessError;
        private int calls;

        FakeCreateTransferUseCase(int failuresBeforeSuccess) {
            super(null, null, null, null);
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public IdempotentResult<TransferResult> transfer(TransferCommand command) {
            calls++;
            if (businessError != null) {
                throw businessError;
            }
            if (calls <= failuresBeforeSuccess) {
                throw new OptimisticLockingFailureException("versao da carteira mudou");
            }
            return new IdempotentResult<>(result, false);
        }
    }
}
