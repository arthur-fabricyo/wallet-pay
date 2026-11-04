package ElFabrica.Wallet_pay.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TransferOutboxIntegrationTest extends AbstractIntegrationTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void successfulTransferWritesOneOutboxRow() throws Exception {
        TestUser sender = createVerifiedUser("Ana", "500.00");
        TestUser receiver = createVerifiedUser("Bruno", "0.00");

        transfer(sender, receiver, "100.00", "key-1").andExpect(status().isCreated());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select event_type, aggregate_id, payload::text as payload from outbox_events");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("event_type")).isEqualTo("TransferCompleted");
            JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
            assertThat(payload.path("senderUserId").asString()).isEqualTo(sender.userId().toString());
            assertThat(payload.path("receiverUserId").asString()).isEqualTo(receiver.userId().toString());
            assertThat(payload.path("amount").asString()).isEqualTo("100.00");
            assertThat(payload.path("currency").asString()).isEqualTo("BRL");
            assertThat(payload.path("transactionId").asString()).isEqualTo(row.get("aggregate_id").toString());
        });
    }

    @Test
    void insufficientBalanceWritesNoOutboxRow() throws Exception {
        TestUser sender = createVerifiedUser("Ana", "10.00");
        TestUser receiver = createVerifiedUser("Bruno", "0.00");

        transfer(sender, receiver, "100.00", "key-2").andExpect(status().is4xxClientError());

        assertThat(outboxRows()).isZero();
    }

    @Test
    void limitExceededWritesNoOutboxRow() throws Exception {
        TestUser sender = createVerifiedUser("Ana", "20000.00");
        TestUser receiver = createVerifiedUser("Bruno", "0.00");

        transfer(sender, receiver, "5000.01", "key-3").andExpect(status().is4xxClientError());

        assertThat(outboxRows()).isZero();
    }

    @Test
    void idempotentReplayDoesNotDuplicateEvent() throws Exception {
        TestUser sender = createVerifiedUser("Ana", "500.00");
        TestUser receiver = createVerifiedUser("Bruno", "0.00");

        transfer(sender, receiver, "100.00", "same-key").andExpect(status().isCreated());
        transfer(sender, receiver, "100.00", "same-key");

        assertThat(outboxRows()).isEqualTo(1);
    }

    @Test
    void transferStillSucceedsWhenNothingIsPublished() throws Exception {
        TestUser sender = createVerifiedUser("Ana", "500.00");
        TestUser receiver = createVerifiedUser("Bruno", "0.00");

        String body = transfer(sender, receiver, "100.00", "key-5").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String transactionId = jsonMapper.readTree(body).path("transactionId").asString();

        assertThat(balanceOf(sender)).isEqualByComparingTo("400.00");
        assertThat(balanceOf(receiver)).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where published_at is null", Long.class)).isEqualTo(1);
        mockMvc.perform(get("/transactions/{id}/receipt", transactionId)
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk());
    }

    private long outboxRows() {
        return jdbcTemplate.queryForObject("select count(*) from outbox_events", Long.class);
    }

    private ResultActions transfer(TestUser sender, TestUser receiver, String amount, String key) throws Exception {
        return mockMvc.perform(post("/transactions/transfers")
                .header("Authorization", bearer(sender))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receiverUserId": "%s", "amount": "%s"}
                        """.formatted(receiver.userId(), amount)));
    }
}
