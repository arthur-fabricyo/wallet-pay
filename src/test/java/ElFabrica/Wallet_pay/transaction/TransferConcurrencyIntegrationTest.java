package ElFabrica.Wallet_pay.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import ElFabrica.Wallet_pay.transaction.service.InsufficientBalanceException;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferService;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransferConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Test
    void shouldKeepBalancesCorrectUnderConcurrentTransfers() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "100.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        BigDecimal totalBefore = totalBalance();

        List<String> outcomes = runConcurrently(20, () -> transferAsClient(sender, receiver, "10.00"));

        assertThat(outcomes).filteredOn("SUCCESS"::equals).hasSize(10);
        assertThat(outcomes).filteredOn("INSUFFICIENT_BALANCE"::equals).hasSize(10);
        assertThat(balanceOf(sender)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("100.00");
        assertThat(totalBalance()).isEqualByComparingTo(totalBefore);
        assertThat(jdbcTemplate.queryForObject("select count(*) from wallets where balance < 0", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from transactions", Long.class)).isEqualTo(10L);
    }

    private String transferAsClient(TestUser sender, TestUser receiver, String amount) {
        String idempotencyKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                transferService.transfer(new TransferCommand(sender.userId(), receiver.userId(), amount, null, idempotencyKey));
                return "SUCCESS";
            } catch (InsufficientBalanceException exception) {
                return "INSUFFICIENT_BALANCE";
            } catch (BalanceConflictException exception) {
                // o cliente repete depois de 409 BALANCE_CONFLICT
            }
        }
        throw new AssertionError("Transferencia nao terminou depois de 50 tentativas do cliente");
    }
}
