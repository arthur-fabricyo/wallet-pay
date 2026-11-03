package ElFabrica.Wallet_pay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class LimitsApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldReturnGlobalLimitsAndUsage() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser user = createVerifiedUser("Joao Silva", "1000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        transfer(user, receiver, "350.00").andExpect(status().isCreated());
        deposit(user, "150.00").andExpect(status().isCreated());

        mockMvc.perform(get("/wallets/me/limits").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer.perOperation.effective").value("5000.00"))
                .andExpect(jsonPath("$.transfer.perOperation.global").value("5000.00"))
                .andExpect(jsonPath("$.transfer.perOperation.custom").value(nullValue()))
                .andExpect(jsonPath("$.transfer.daily.used").value("350.00"))
                .andExpect(jsonPath("$.transfer.daily.available").value("9650.00"))
                .andExpect(jsonPath("$.transfer.monthly.used").value("350.00"))
                .andExpect(jsonPath("$.transfer.monthly.available").value("49650.00"))
                .andExpect(jsonPath("$.transfer.nightPerOperation.effective").value("1000.00"))
                .andExpect(jsonPath("$.transfer.nightWindow.used").value("0.00"))
                .andExpect(jsonPath("$.transfer.nightWindow.available").value("1000.00"))
                .andExpect(jsonPath("$.deposit.perOperation.effective").value("10000.00"))
                .andExpect(jsonPath("$.deposit.daily.used").value("150.00"))
                .andExpect(jsonPath("$.deposit.daily.available").value("19850.00"))
                .andExpect(jsonPath("$.night.start").value("20:00"))
                .andExpect(jsonPath("$.night.end").value("06:00"))
                .andExpect(jsonPath("$.night.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.night.active").value(false));
    }

    @Test
    void shouldReportActiveNightWindowAndItsUsage() throws Exception {
        useSaoPauloTime("2026-10-15T23:00");
        TestUser user = createVerifiedUser("Joao Silva", "1000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        transfer(user, receiver, "300.00").andExpect(status().isCreated());

        mockMvc.perform(get("/wallets/me/limits").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.night.active").value(true))
                .andExpect(jsonPath("$.transfer.nightWindow.used").value("300.00"))
                .andExpect(jsonPath("$.transfer.nightWindow.available").value("700.00"));
    }

    @Test
    void shouldUpdateCustomLimitAndApplyItImmediately() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser user = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        updateLimits(user, """
                {
                  "transferPerOperation": "2000.00"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer.perOperation.effective").value("2000.00"))
                .andExpect(jsonPath("$.transfer.perOperation.global").value("5000.00"))
                .andExpect(jsonPath("$.transfer.perOperation.custom").value("2000.00"))
                .andExpect(jsonPath("$.transfer.daily.custom").value(nullValue()));

        transfer(user, receiver, "2000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields.limitType").value("PER_OPERATION"))
                .andExpect(jsonPath("$.fields.limit").value("2000.00"));

        assertThat(jdbcTemplate.queryForObject(
                "select metadata->>'new.transferPerOperation' from audit_events where action = 'LIMITS_UPDATED'",
                String.class
        )).isEqualTo("2000.00");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_limits_aud where wallet_id = ?", Long.class, user.walletId()
        )).isEqualTo(1L);
    }

    @Test
    void shouldGoBackToTheGlobalLimitWhenValueIsNull() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");
        updateLimits(user, """
                {
                  "transferPerOperation": "2000.00"
                }
                """).andExpect(status().isOk());

        updateLimits(user, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer.perOperation.effective").value("5000.00"))
                .andExpect(jsonPath("$.transfer.perOperation.custom").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "select metadata->>'previous.transferPerOperation' from audit_events where action = 'LIMITS_UPDATED' "
                        + "and metadata ? 'previous.transferPerOperation'",
                String.class
        )).isEqualTo("2000.00");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_limits_aud where wallet_id = ?", Long.class, user.walletId()
        )).isEqualTo(2L);
    }

    @Test
    void shouldRejectCustomLimitAboveTheGlobalOne() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        updateLimits(user, """
                {
                  "transferPerOperation": "5000.01"
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMITS"))
                .andExpect(jsonPath("$.fields.transferPerOperation").value("Limite acima do teto global de 5000.00"));
    }

    @Test
    void shouldRejectIncoherentEffectiveLimits() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        updateLimits(user, """
                {
                  "transferDaily": "1000.00"
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMITS"))
                .andExpect(jsonPath("$.fields.transferDaily")
                        .value("Limite diario deve ser maior ou igual ao limite por operacao"));
    }

    @Test
    void shouldRejectInvalidAmountInLimits() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        updateLimits(user, """
                {
                  "transferPerOperation": "abc"
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void shouldRequireJwtForLimits() throws Exception {
        mockMvc.perform(get("/wallets/me/limits")).andExpect(status().isUnauthorized());
    }

    private ResultActions updateLimits(TestUser user, String body) throws Exception {
        return mockMvc.perform(put("/wallets/me/limits")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions transfer(TestUser sender, TestUser receiver, String amount) throws Exception {
        return mockMvc.perform(post("/transactions/transfers")
                .header("Authorization", bearer(sender))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "receiverUserId": "%s",
                          "amount": "%s"
                        }
                        """.formatted(receiver.userId(), amount)));
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
}
