package ElFabrica.Wallet_pay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferService;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import ElFabrica.Wallet_pay.wallet.service.DepositCommand;
import ElFabrica.Wallet_pay.wallet.service.DepositService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

class DepositIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DepositService depositService;

    @Autowired
    private TransferService transferService;

    @Test
    void shouldCreditOwnWalletAndRegisterDepositTransaction() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "100.00");

        mockMvc.perform(post("/wallets/me/deposits").header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "150.00",
                                  "description": "Aporte inicial"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("150.00"))
                .andExpect(jsonPath("$.description").value("Aporte inicial"))
                .andExpect(jsonPath("$.wallet.id").value(user.walletId().toString()))
                .andExpect(jsonPath("$.wallet.balance").value("250.00"));

        assertThat(balanceOf(user)).isEqualByComparingTo("250.00");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select type, sender_wallet_id, receiver_wallet_id from transactions"
        );
        assertThat(row.get("type")).isEqualTo("DEPOSIT");
        assertThat(row.get("sender_wallet_id")).isNull();
        assertThat(row.get("receiver_wallet_id")).isEqualTo(user.walletId());
    }

    @Test
    void shouldRejectDepositWithoutJwt() throws Exception {
        mockMvc.perform(post("/wallets/me/deposits").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "150.00"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00", "abc", "10.123", "100000000000000000000"})
    void shouldRejectInvalidAmountWithoutChangingBalance(String amount) throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "100.00");

        mockMvc.perform(post("/wallets/me/deposits").header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "%s"
                                }
                                """.formatted(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        assertThat(balanceOf(user)).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from transactions", Long.class)).isZero();
    }

    @Test
    void shouldRejectMissingAmountWithInvalidAmountCode() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        mockMvc.perform(post("/wallets/me/deposits").header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.message").value("Valor e obrigatorio"));
    }

    @Test
    void databaseShouldRejectTransferWithoutSender() {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into transactions (id, sender_wallet_id, receiver_wallet_id, type, status, amount, currency, created_at)
                values (?, null, ?, 'TRANSFER', 'COMPLETED', 10.00, 'BRL', now())
                """, UUID.randomUUID(), user.walletId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseShouldRejectDepositWithSender() {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        TestUser other = createVerifiedUser("Maria Souza", "0.00");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into transactions (id, sender_wallet_id, receiver_wallet_id, type, status, amount, currency, created_at)
                values (?, ?, ?, 'DEPOSIT', 'COMPLETED', 10.00, 'BRL', now())
                """, UUID.randomUUID(), other.walletId(), user.walletId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldListDepositInStatementAndFilterByType() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        TestUser other = createVerifiedUser("Maria Souza", "50.00");
        depositService.deposit(new DepositCommand(user.userId(), "150.00", "Aporte", UUID.randomUUID().toString()));
        transferService.transfer(new TransferCommand(other.userId(), user.userId(), "20.00", null, UUID.randomUUID().toString()));

        mockMvc.perform(get("/wallets/me/statement").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/wallets/me/statement").param("type", "DEPOSIT").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].counterpartyName").value(nullValue()))
                .andExpect(jsonPath("$.items[0].amount").value("150.00"));
    }

    @Test
    void shouldRefuseReceiptForDeposit() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        String body = mockMvc.perform(post("/wallets/me/deposits").header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "10.00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        String transactionId = JsonPath.read(body, "$.transactionId");

        mockMvc.perform(get("/transactions/{id}/receipt", transactionId).header("Authorization", bearer(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECEIPT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Comprovante disponivel apenas para transferencias"));
    }

    @Test
    void shouldSumConcurrentDepositsCorrectly() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        List<String> outcomes = runConcurrently(10, () -> depositAsClient(user, "10.00"));

        assertThat(outcomes).containsOnly("SUCCESS");
        assertThat(balanceOf(user)).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from transactions", Long.class)).isEqualTo(10L);
    }

    private String depositAsClient(TestUser user, String amount) {
        String idempotencyKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                depositService.deposit(new DepositCommand(user.userId(), amount, null, idempotencyKey));
                return "SUCCESS";
            } catch (BalanceConflictException exception) {
                // o cliente repete depois de 409 BALANCE_CONFLICT
            }
        }
        throw new AssertionError("Deposito nao terminou depois de 50 tentativas do cliente");
    }
}
