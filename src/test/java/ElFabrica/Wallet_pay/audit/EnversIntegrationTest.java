package ElFabrica.Wallet_pay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferService;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EnversIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Test
    void shouldRecordTransferRevisionWithUserAndRequestId() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "100.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        String body = mockMvc.perform(post("/transactions/transfers")
                        .header("Authorization", bearer(sender))
                        .header("Idempotency-Key", "envers-1")
                        .header("X-Request-Id", "req-envers-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "10.00"
                                }
                                """.formatted(receiver.userId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID transactionId = UUID.fromString(JsonPath.read(body, "$.transactionId"));

        Long revision = jdbcTemplate.queryForObject(
                "select rev from transactions_aud where id = ?", Long.class, transactionId
        );
        assertThat(jdbcTemplate.queryForList(
                "select id from wallets_aud where rev = ? and revtype = 1", UUID.class, revision
        )).containsExactlyInAnyOrder(sender.walletId(), receiver.walletId());
        assertThat(jdbcTemplate.queryForObject(
                "select balance from wallets_aud where rev = ? and id = ?", BigDecimal.class, revision, sender.walletId()
        )).isEqualByComparingTo("90.00");

        Map<String, Object> revisionInfo = jdbcTemplate.queryForMap(
                "select user_id, request_id, \"timestamp\" as revision_timestamp from revinfo where id = ?", revision
        );
        assertThat(revisionInfo.get("user_id")).isEqualTo(sender.userId());
        assertThat(revisionInfo.get("request_id")).isEqualTo("req-envers-1");
        assertThat((Long) revisionInfo.get("revision_timestamp")).isPositive();
    }

    @Test
    void shouldNotStorePasswordHashInUserHistory() {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        assertThat(jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'users_aud'", String.class
        )).contains("email", "document").doesNotContain("password_hash");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users_aud where id = ?", Long.class, user.userId()
        )).isEqualTo(1L);
    }

    @Test
    void shouldWriteOneRevisionPerCommittedTransferEvenWithRetries() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "100.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        List<Boolean> outcomes = runConcurrently(10, () -> transferAsClient(sender, receiver));

        assertThat(outcomes).containsOnly(true);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallets_aud where id = ?", Long.class, sender.walletId()
        )).isEqualTo(11L);
        assertThat(jdbcTemplate.queryForObject(
                "select max(version) from wallets_aud where id = ?", Long.class, sender.walletId()
        )).isEqualTo(10L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from transactions_aud", Long.class)).isEqualTo(10L);
    }

    private boolean transferAsClient(TestUser sender, TestUser receiver) {
        String idempotencyKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                transferService.transfer(new TransferCommand(sender.userId(), receiver.userId(), "10.00", null, idempotencyKey));
                return true;
            } catch (BalanceConflictException exception) {
                // o cliente repete depois de 409 BALANCE_CONFLICT
            }
        }
        return false;
    }
}
