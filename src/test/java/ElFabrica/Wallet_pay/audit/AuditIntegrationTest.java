package ElFabrica.Wallet_pay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.CapturingEmailVerificationSender;
import ElFabrica.Wallet_pay.support.CapturingPasswordRecoverySender;
import ElFabrica.Wallet_pay.support.TestUser;
import ElFabrica.Wallet_pay.wallet.service.DepositCommand;
import ElFabrica.Wallet_pay.wallet.service.DepositService;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuditIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CapturingEmailVerificationSender emailVerificationSender;

    @Autowired
    private CapturingPasswordRecoverySender passwordRecoverySender;

    @Autowired
    private DepositService depositService;

    @Test
    void shouldRecordTransferCreatedWithRequestContext() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "100.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        String body = transfer(sender, receiver.userId(), "10.00", "req-audit-1")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String transactionId = JsonPath.read(body, "$.transactionId");

        Map<String, Object> event = jdbcTemplate.queryForMap(
                "select * from audit_events where action = 'TRANSFER_CREATED'"
        );
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_type")).isEqualTo("USER");
        assertThat(event.get("actor_user_id")).isEqualTo(sender.userId());
        assertThat(event.get("resource_type")).isEqualTo("TRANSACTION");
        assertThat(event.get("resource_id")).isEqualTo(UUID.fromString(transactionId));
        assertThat(event.get("failure_code")).isNull();
        assertThat(event.get("request_id")).isEqualTo("req-audit-1");
        assertThat(event.get("ip")).isEqualTo("127.0.0.1");
        assertThat(metadataValue("TRANSFER_CREATED", "amount")).isEqualTo("10.00");
    }

    @Test
    void shouldRecordRejectedTransferEvenAfterRollback() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "0.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver.userId(), "50.00", "req-audit-2")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        Map<String, Object> event = jdbcTemplate.queryForMap(
                "select * from audit_events where action = 'TRANSFER_REJECTED'"
        );
        assertThat(event.get("outcome")).isEqualTo("FAILURE");
        assertThat(event.get("failure_code")).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(event.get("resource_type")).isEqualTo("WALLET");
        assertThat(event.get("actor_user_id")).isEqualTo(sender.userId());
        assertThat(event.get("request_id")).isEqualTo("req-audit-2");
        assertThat(jdbcTemplate.queryForObject("select count(*) from transactions", Long.class)).isZero();
    }

    @Test
    void shouldRecordRejectionForUnknownReceiverWithoutKeepingTheIdempotencyKey() throws Exception {
        TestUser sender = createVerifiedUser("Joao Silva", "100.00");

        transfer(sender, UUID.randomUUID(), "10.00", "req-audit-3")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "select failure_code from audit_events where action = 'TRANSFER_REJECTED'", String.class
        )).isEqualTo("WALLET_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject("select count(*) from idempotency_keys", Long.class)).isZero();
        assertThat(balanceOf(sender)).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRecordDepositCreatedAndRejected() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        deposit(user, "15.00").andExpect(status().isCreated());
        deposit(user, "0").andExpect(status().isBadRequest());

        assertThat(metadataValue("DEPOSIT_CREATED", "walletId")).isEqualTo(user.walletId().toString());
        assertThat(jdbcTemplate.queryForObject(
                "select failure_code from audit_events where action = 'DEPOSIT_REJECTED'", String.class
        )).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void shouldTruncateAnOversizedRejectedAmount() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        String oversized = "9".repeat(200);

        deposit(user, oversized)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        assertThat(metadataValue("DEPOSIT_REJECTED", "amount")).isEqualTo("9".repeat(32) + "...");
    }

    @Test
    void shouldRecordTheAuthenticationLifecycleWithoutSensitiveData() throws Exception {
        String email = "joao.audit@email.com";
        String document = "52998224725";

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Joao Silva",
                                  "email": "%s",
                                  "password": "%s",
                                  "document": "%s"
                                }
                                """.formatted(email, DEFAULT_PASSWORD, document)))
                .andExpect(status().isCreated());
        login(email, DEFAULT_PASSWORD).andExpect(status().isForbidden());

        String verificationToken = emailVerificationSender.lastTokenFor(email);
        mockMvc.perform(post("/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(verificationToken)))
                .andExpect(status().isNoContent());

        login(email, "SenhaErrada1").andExpect(status().isUnauthorized());
        String loginBody = login(email, DEFAULT_PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.accessToken");
        String refreshToken = JsonPath.read(loginBody, "$.refreshToken");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        requestPasswordRecovery(email);
        requestPasswordRecovery("ninguem@email.com");
        String resetToken = passwordRecoverySender.lastTokenFor(email);
        mockMvc.perform(post("/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NovaSenha123"
                                }
                                """.formatted(resetToken)))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForList("select action from audit_events", String.class))
                .containsExactlyInAnyOrder(
                        "USER_REGISTERED",
                        "LOGIN_FAILED",
                        "EMAIL_VERIFIED",
                        "LOGIN_FAILED",
                        "LOGIN_SUCCEEDED",
                        "LOGOUT",
                        "PASSWORD_RESET_REQUESTED",
                        "PASSWORD_RESET_COMPLETED"
                );
        assertThat(jdbcTemplate.queryForList(
                "select metadata->>'reason' from audit_events where action = 'LOGIN_FAILED'", String.class
        )).containsExactlyInAnyOrder("EMAIL_NOT_VERIFIED", "INVALID_CREDENTIALS");

        String allMetadata = jdbcTemplate.queryForObject(
                "select string_agg(metadata::text, ' ') from audit_events", String.class
        );
        assertThat(allMetadata).doesNotContain(
                DEFAULT_PASSWORD,
                "SenhaErrada1",
                "NovaSenha123",
                document,
                verificationToken,
                resetToken,
                refreshToken,
                accessToken
        );
    }

    @Test
    void auditEventsShouldBeAppendOnly() {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        depositService.deposit(new DepositCommand(user.userId(), "10.00", null, "append-only-1"));

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_events set action = 'ALTERADO'"))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_events"))
                .hasStackTraceContaining("append-only");
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_events", Long.class)).isEqualTo(1L);
    }

    private ResultActions transfer(TestUser sender, UUID receiverUserId, String amount, String requestId) throws Exception {
        return mockMvc.perform(post("/transactions/transfers")
                .header("Authorization", bearer(sender))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "receiverUserId": "%s",
                          "amount": "%s"
                        }
                        """.formatted(receiverUserId, amount)));
    }

    private ResultActions deposit(TestUser user, String amount) throws Exception {
        return mockMvc.perform(post("/wallets/me/deposits")
                .header("Authorization", bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": "%s"
                        }
                        """.formatted(amount)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private void requestPasswordRecovery(String email) throws Exception {
        mockMvc.perform(post("/auth/password-recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isNoContent());
    }

    private String metadataValue(String action, String key) {
        return jdbcTemplate.queryForObject(
                "select metadata ->> cast(? as text) from audit_events where action = ?",
                String.class,
                key,
                action
        );
    }
}
