package ElFabrica.Wallet_pay.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class NotificationPreferenceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void defaultsAreEmailOffPushOn() throws Exception {
        TestUser user = createVerifiedUser("Ana", "0.00");

        mockMvc.perform(get("/notifications/preferences").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.channel=='PUSH')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL')].enabled").value(false));
    }

    @Test
    void enablingEmailPersistsAndIsReflectedOnGet() throws Exception {
        TestUser user = createVerifiedUser("Ana", "0.00");

        update(user, "EMAIL", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/notifications/preferences").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL')].enabled").value(true));
    }

    @Test
    void updatingTwiceKeepsSingleRow() throws Exception {
        TestUser user = createVerifiedUser("Ana", "0.00");

        update(user, "EMAIL", true).andExpect(status().isOk());
        update(user, "EMAIL", false).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notification_preferences where user_id = ?", Long.class, user.userId()))
                .isEqualTo(1);
        mockMvc.perform(get("/notifications/preferences").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL')].enabled").value(false));
    }

    @Test
    void disablingPushIsRejectedWith400() throws Exception {
        TestUser user = createVerifiedUser("Ana", "0.00");

        update(user, "PUSH", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void unknownChannelIsRejectedWith400() throws Exception {
        TestUser user = createVerifiedUser("Ana", "0.00");

        update(user, "SMS", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/notifications/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/notifications/preferences/EMAIL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotSeeEachOthersPreferences() throws Exception {
        TestUser ana = createVerifiedUser("Ana", "0.00");
        TestUser bruno = createVerifiedUser("Bruno", "0.00");

        update(ana, "EMAIL", true).andExpect(status().isOk());

        mockMvc.perform(get("/notifications/preferences").header("Authorization", bearer(bruno)))
                .andExpect(jsonPath("$[?(@.channel=='EMAIL')].enabled").value(false));
    }

    @Test
    void missingEnabledFieldIsRejectedWith400() throws Exception {
        TestUser user = createVerifiedUser("Ana", "0.00");

        mockMvc.perform(put("/notifications/preferences/EMAIL")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions update(TestUser user, String channel, boolean enabled) throws Exception {
        return mockMvc.perform(put("/notifications/preferences/" + channel)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": " + enabled + "}"));
    }
}
