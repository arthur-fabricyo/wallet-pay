package ElFabrica.Wallet_pay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import org.junit.jupiter.api.Test;

class WalletBalanceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldReadBalanceFromRealDatabase() throws Exception {
        TestUser user = createVerifiedUser("Joao Silva", "100.00");

        mockMvc.perform(get("/wallets/me/balance").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(user.walletId().toString()))
                .andExpect(jsonPath("$.balance").value("100.00"))
                .andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    void shouldStartEveryTestWithEmptyTables() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from wallets", Long.class)).isZero();
    }
}
