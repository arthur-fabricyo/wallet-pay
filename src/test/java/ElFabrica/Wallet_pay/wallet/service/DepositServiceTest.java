package ElFabrica.Wallet_pay.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.config.ResilienceConfig;
import ElFabrica.Wallet_pay.support.RecordingAuditLogger;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class DepositServiceTest {

    private static final DepositCommand COMMAND = new DepositCommand(UUID.randomUUID(), "10.00", null, "chave-1");

    @Test
    void shouldRetryAndThenReturnTheDeposit() {
        FakeCreateDepositUseCase useCase = new FakeCreateDepositUseCase(1);
        DepositService service = new DepositService(new ResilienceConfig().balanceRetryTemplate(), useCase, new RecordingAuditLogger());

        assertThat(service.deposit(COMMAND).result()).isSameAs(useCase.result);
        assertThat(useCase.calls).isEqualTo(2);
    }

    @Test
    void shouldTranslateExhaustedRetriesIntoBalanceConflict() {
        FakeCreateDepositUseCase useCase = new FakeCreateDepositUseCase(Integer.MAX_VALUE);
        DepositService service = new DepositService(new ResilienceConfig().balanceRetryTemplate(), useCase, new RecordingAuditLogger());

        assertThatThrownBy(() -> service.deposit(COMMAND))
                .isInstanceOf(BalanceConflictException.class)
                .hasMessage("Operacao concorrente na carteira, tente novamente");
        assertThat(useCase.calls).isEqualTo(4);
    }

    static class FakeCreateDepositUseCase extends CreateDepositUseCase {

        private final int failuresBeforeSuccess;
        private final DepositResult result = new DepositResult(
                UUID.randomUUID(), null, null, "10.00", "BRL", null, null, null, null
        );
        private int calls;

        FakeCreateDepositUseCase(int failuresBeforeSuccess) {
            super(null, null, null, null, null);
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public IdempotentResult<DepositResult> deposit(DepositCommand command) {
            calls++;
            if (calls <= failuresBeforeSuccess) {
                throw new OptimisticLockingFailureException("versao da carteira mudou");
            }
            return new IdempotentResult<>(result, false);
        }
    }
}
