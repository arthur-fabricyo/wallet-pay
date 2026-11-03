package ElFabrica.Wallet_pay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.CapturingEmailVerificationSender;
import ElFabrica.Wallet_pay.support.TestCpf;
import ElFabrica.Wallet_pay.support.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class FullFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CapturingEmailVerificationSender emailVerificationSender;

    private final List<MvcResult> responses = new ArrayList<>();

    @Test
    void shouldRunTheCompleteFinancialFlow() throws Exception {
        Session joao = registerVerifyAndLogin("Joao Silva", "joao.e2e@email.com");
        Session maria = registerVerifyAndLogin("Maria Souza", "maria.e2e@email.com");

        call(post("/wallets/me/deposits")
                .header("Authorization", joao.bearer())
                .header("Idempotency-Key", "e2e-deposito-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": "500.00"
                        }
                        """), 201);
        MvcResult transfer = call(post("/transactions/transfers")
                .header("Authorization", joao.bearer())
                .header("Idempotency-Key", "e2e-transferencia-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "receiverUserId": "%s",
                          "amount": "120.00",
                          "description": "Aluguel"
                        }
                        """.formatted(maria.userId())), 201);
        String transferId = read(transfer, "$.transactionId");

        assertThat(read(call(get("/wallets/me/balance").header("Authorization", joao.bearer()), 200), "$.balance"))
                .isEqualTo("380.00");
        assertThat(read(call(get("/wallets/me/balance").header("Authorization", maria.bearer()), 200), "$.balance"))
                .isEqualTo("120.00");

        String joaoStatement = body(call(get("/wallets/me/statement").header("Authorization", joao.bearer()), 200));
        List<String> joaoDepositDirections = JsonPath.read(joaoStatement, "$.items[?(@.type == 'DEPOSIT')].direction");
        List<String> joaoTransferDirections = JsonPath.read(joaoStatement, "$.items[?(@.type == 'TRANSFER')].direction");
        List<String> joaoCounterparties = JsonPath.read(joaoStatement, "$.items[?(@.type == 'TRANSFER')].counterpartyName");
        assertThat(joaoDepositDirections).containsExactly("IN");
        assertThat(joaoTransferDirections).containsExactly("OUT");
        assertThat(joaoCounterparties).containsExactly("Maria Souza");

        String mariaStatement = body(call(get("/wallets/me/statement").header("Authorization", maria.bearer()), 200));
        assertThat((Integer) JsonPath.read(mariaStatement, "$.totalElements")).isEqualTo(1);
        assertThat((String) JsonPath.read(mariaStatement, "$.items[0].direction")).isEqualTo("IN");
        assertThat((String) JsonPath.read(mariaStatement, "$.items[0].counterpartyName")).isEqualTo("Joao Silva");

        MvcResult receipt = call(get("/transactions/{id}/receipt", transferId).header("Authorization", maria.bearer()), 200);
        assertThat(read(receipt, "$.amount")).isEqualTo("120.00");
        assertThat(read(receipt, "$.senderName")).isEqualTo("Joao Silva");

        assertThat(auditActionCounts())
                .containsEntry("USER_REGISTERED", 2L)
                .containsEntry("EMAIL_VERIFIED", 2L)
                .containsEntry("LOGIN_SUCCEEDED", 2L)
                .containsEntry("DEPOSIT_CREATED", 1L)
                .containsEntry("TRANSFER_CREATED", 1L);

        UUID joaoWalletId = jdbcTemplate.queryForObject(
                "select id from wallets where user_id = ?", UUID.class, joao.userId()
        );
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transactions_aud where id = ?", Long.class, UUID.fromString(transferId)
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallets_aud where id = ?", Long.class, joaoWalletId
        )).isEqualTo(3L);

        assertThat(responses).isNotEmpty().allSatisfy(response ->
                assertThat(response.getResponse().getHeader("X-Request-Id")).isNotBlank());
    }

    @Test
    void shouldReturnUniformErrorsOnFailurePaths() throws Exception {
        TestUser joao = createVerifiedUser("Joao Silva", "100.00");
        TestUser maria = createVerifiedUser("Maria Souza", "0.00");
        TestUser rico = createVerifiedUser("Rico Souza", "6000.00");

        assertError(call(get("/wallets/me/balance"), 401), "UNAUTHORIZED");

        assertError(call(transferRequest(joao, maria, "150.00", "e2e-erro-1"), 422), "INSUFFICIENT_BALANCE");
        assertThat(jdbcTemplate.queryForObject(
                "select failure_code from audit_events where action = 'TRANSFER_REJECTED'", String.class
        )).isEqualTo("INSUFFICIENT_BALANCE");

        assertError(call(transferRequest(rico, maria, "5000.01", "e2e-erro-2"), 422), "LIMIT_EXCEEDED");

        call(transferRequest(joao, maria, "10.00", "e2e-erro-3"), 201);
        assertError(call(transferRequest(joao, maria, "20.00", "e2e-erro-3"), 422), "IDEMPOTENCY_KEY_REUSED");

        assertError(call(post("/transactions/transfers")
                .header("Authorization", bearer(joao))
                .header("Idempotency-Key", "e2e-erro-4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"), 400), "MALFORMED_REQUEST");

        assertError(call(get("/rota-inexistente").header("Authorization", bearer(joao)), 404), "ROUTE_NOT_FOUND");

        MvcResult unexpected = call(get("/test-support/boom").header("Authorization", bearer(joao)), 500);
        assertError(unexpected, "INTERNAL_ERROR");
        assertThat(body(unexpected))
                .doesNotContain("detalhe interno secreto")
                .doesNotContain("IllegalStateException");
    }

    private Session registerVerifyAndLogin(String name, String email) throws Exception {
        MvcResult created = call(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "%s",
                          "email": "%s",
                          "password": "%s",
                          "document": "%s"
                        }
                        """.formatted(name, email, DEFAULT_PASSWORD, TestCpf.generate())), 201);
        UUID userId = UUID.fromString(read(created, "$.id"));

        call(post("/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "token": "%s"
                        }
                        """.formatted(emailVerificationSender.lastTokenFor(email))), 204);

        MvcResult login = call(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, DEFAULT_PASSWORD)), 200);
        return new Session(userId, read(login, "$.accessToken"));
    }

    private MockHttpServletRequestBuilder transferRequest(TestUser sender, TestUser receiver, String amount, String key) {
        return post("/transactions/transfers")
                .header("Authorization", bearer(sender))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "receiverUserId": "%s",
                          "amount": "%s"
                        }
                        """.formatted(receiver.userId(), amount));
    }

    private MvcResult call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        responses.add(result);
        return result;
    }

    private void assertError(MvcResult result, String expectedCode) throws Exception {
        String body = body(result);
        String requestId = result.getResponse().getHeader("X-Request-Id");

        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo(expectedCode);
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(result.getResponse().getStatus());
        assertThat((Map<String, Object>) JsonPath.read(body, "$.fields")).isNotNull();
        assertThat(requestId).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.requestId")).isEqualTo(requestId);
    }

    private Map<String, Long> auditActionCounts() {
        return jdbcTemplate.query("select action, count(*) as total from audit_events group by action", resultSet -> {
            Map<String, Long> counts = new HashMap<>();
            while (resultSet.next()) {
                counts.put(resultSet.getString("action"), resultSet.getLong("total"));
            }
            return counts;
        });
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    private record Session(UUID userId, String accessToken) {

        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
