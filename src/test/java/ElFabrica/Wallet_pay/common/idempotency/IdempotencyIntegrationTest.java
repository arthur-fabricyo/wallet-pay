package ElFabrica.Wallet_pay.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import ElFabrica.Wallet_pay.wallet.service.DepositCommand;
import ElFabrica.Wallet_pay.wallet.service.DepositResult;
import ElFabrica.Wallet_pay.wallet.service.DepositService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DepositService depositService;

    @Autowired
    private IdempotencyKeyCleanupJob cleanupJob;

    @Test
    void shouldRequireKeyOnDepositsAndTransfers() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "100.00");
        TestUser other = createVerifiedUser("Maria Souza", "0.00");

        deposit(user, null, "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        transfer(user, other, null, "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(count("transactions")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldTreatBlankKeyAsMissing(String key) throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        deposit(user, key, "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(count("idempotency_keys")).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void shouldRejectInvalidKey(String key) throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        deposit(user, key, "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"));
    }

    static Stream<String> invalidKeys() {
        return Stream.of("chave com espaco", "chave!", "a".repeat(101));
    }

    @Test
    void shouldReplaySameDepositAndExecuteItOnce() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        String first = deposit(user, "deposito-1", "10.0")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getContentAsString();
        String second = deposit(user, "deposito-1", "10.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        String firstId = JsonPath.read(first, "$.transactionId");
        String secondId = JsonPath.read(second, "$.transactionId");
        assertThat(secondId).isEqualTo(firstId);
        assertThat(balanceOf(user)).isEqualByComparingTo("10.00");
        assertThat(count("transactions")).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select response_status from idempotency_keys", Integer.class))
                .isEqualTo(201);
    }

    @Test
    void shouldReplayTheOriginalSnapshotEvenAfterTheBalanceChanged() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        deposit(user, "deposito-1", "10.00").andExpect(status().isCreated());
        deposit(user, "deposito-2", "5.00").andExpect(status().isCreated());

        deposit(user, "deposito-1", "10.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.wallet.balance").value("10.00"));

        assertThat(balanceOf(user)).isEqualByComparingTo("15.00");
    }

    @Test
    void shouldReplayTransferAndDebitOnce() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "100.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver, "transferencia-1", "30.00").andExpect(status().isCreated());
        transfer(sender, receiver, "transferencia-1", "30.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(balanceOf(sender)).isEqualByComparingTo("70.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldRejectKeyReusedWithDifferentPayload() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        deposit(user, "deposito-1", "10.00").andExpect(status().isCreated());

        deposit(user, "deposito-1", "11.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key ja utilizada com outro payload"));

        assertThat(balanceOf(user)).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldScopeKeysPerUser() throws Exception {
        TestUser joao = createVerifiedUser("Joao Silva", "0.00");
        TestUser maria = createVerifiedUser("Maria Souza", "0.00");

        deposit(joao, "mesma-chave", "10.00")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        deposit(maria, "mesma-chave", "10.00")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        assertThat(count("transactions")).isEqualTo(2L);
    }

    @Test
    void shouldNotKeepKeyWhenOperationIsRejected() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "0.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver, "transferencia-1", "50.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        assertThat(count("idempotency_keys")).isZero();

        deposit(sender, "deposito-1", "100.00").andExpect(status().isCreated());
        transfer(sender, receiver, "transferencia-1", "50.00")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void shouldExecuteOnceUnderConcurrentRequestsWithTheSameKey() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        DepositCommand command = new DepositCommand(user.userId(), "10.00", null, "mesma-chave");

        List<IdempotentResult<DepositResult>> outcomes = runConcurrently(10, () -> depositService.deposit(command));

        assertThat(outcomes)
                .extracting(outcome -> outcome.result().transactionId())
                .containsOnly(outcomes.get(0).result().transactionId());
        assertThat(outcomes).filteredOn(outcome -> !outcome.replayed()).hasSize(1);
        assertThat(count("transactions")).isEqualTo(1L);
        assertThat(balanceOf(user)).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldExecuteAgainAfterTheKeyExpired() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        deposit(user, "chave-expira", "10.00").andExpect(status().isCreated());
        jdbcTemplate.update("update idempotency_keys set expires_at = now() - interval '1 hour'");

        deposit(user, "chave-expira", "10.00")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        assertThat(count("transactions")).isEqualTo(2L);
        assertThat(balanceOf(user)).isEqualByComparingTo("20.00");
    }

    @Test
    void cleanupJobShouldDeleteOnlyExpiredKeys() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        deposit(user, "chave-velha", "10.00").andExpect(status().isCreated());
        deposit(user, "chave-nova", "10.00").andExpect(status().isCreated());
        jdbcTemplate.update(
                "update idempotency_keys set expires_at = now() - interval '1 minute' where idempotency_key = ?",
                "chave-velha"
        );

        assertThat(cleanupJob.purgeExpiredKeys()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("select idempotency_key from idempotency_keys", String.class))
                .containsExactly("chave-nova");
    }

    private ResultActions deposit(TestUser user, String key, String amount) throws Exception {
        MockHttpServletRequestBuilder request = post("/wallets/me/deposits")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": "%s"
                        }
                        """.formatted(amount));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions transfer(TestUser sender, TestUser receiver, String key, String amount) throws Exception {
        MockHttpServletRequestBuilder request = post("/transactions/transfers")
                .header("Authorization", bearer(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "receiverUserId": "%s",
                          "amount": "%s"
                        }
                        """.formatted(receiver.userId(), amount));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }
}
