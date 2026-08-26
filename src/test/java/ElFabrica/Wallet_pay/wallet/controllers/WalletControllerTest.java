package ElFabrica.Wallet_pay.wallet.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.config.SecurityConfig;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.wallet.service.GetWalletBalanceUseCase;
import ElFabrica.Wallet_pay.wallet.service.GetWalletStatementUseCase;
import ElFabrica.Wallet_pay.wallet.service.InvalidStatementQueryException;
import ElFabrica.Wallet_pay.wallet.service.StatementDirection;
import ElFabrica.Wallet_pay.wallet.service.StatementEntryResult;
import ElFabrica.Wallet_pay.wallet.service.StatementQuery;
import ElFabrica.Wallet_pay.wallet.service.StatementResult;
import ElFabrica.Wallet_pay.wallet.service.WalletBalanceResult;
import ElFabrica.Wallet_pay.wallet.service.WalletNotFoundException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "wallet-pay.auth.jwt-secret=wallet-pay-test-secret-with-32-bytes")
class WalletControllerTest {

    private static final String SECRET = "wallet-pay-test-secret-with-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeGetWalletBalanceUseCase getWalletBalanceUseCase;

    @Autowired
    private FakeGetWalletStatementUseCase getWalletStatementUseCase;

    @BeforeEach
    void setUp() {
        getWalletBalanceUseCase.result = null;
        getWalletBalanceUseCase.exception = null;
        getWalletBalanceUseCase.userId = null;
        getWalletStatementUseCase.result = null;
        getWalletStatementUseCase.exception = null;
        getWalletStatementUseCase.userId = null;
        getWalletStatementUseCase.query = null;
    }

    @Test
    void shouldReturnAuthenticatedUserBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-08-06T10:30:00Z");
        getWalletBalanceUseCase.result = new WalletBalanceResult(walletId, "0.00", "BRL", updatedAt);

        mockMvc.perform(get("/wallets/me/balance")
                        .header("Authorization", "Bearer " + token(userId, Instant.now().plusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-06T10:30:00Z"));

        org.assertj.core.api.Assertions.assertThat(getWalletBalanceUseCase.userId).isEqualTo(userId);
    }

    @Test
    void shouldRejectBalanceQueryWithoutJwt() throws Exception {
        mockMvc.perform(get("/wallets/me/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBalanceQueryWithExpiredJwt() throws Exception {
        mockMvc.perform(get("/wallets/me/balance")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().minusSeconds(1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnNotFoundWhenAuthenticatedUserHasNoWallet() throws Exception {
        getWalletBalanceUseCase.exception =
                new WalletNotFoundException("Carteira nao encontrada para o usuario autenticado");

        mockMvc.perform(get("/wallets/me/balance")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldReturnAuthenticatedUserStatement() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:30:00Z");
        getWalletStatementUseCase.result = new StatementResult(
                0,
                20,
                1,
                1,
                List.of(new StatementEntryResult(
                        transactionId,
                        TransactionType.TRANSFER,
                        TransactionStatus.COMPLETED,
                        StatementDirection.OUT,
                        "25.50",
                        "BRL",
                        "Maria Souza",
                        "Almoco",
                        now,
                        now
                ))
        );

        mockMvc.perform(get("/wallets/me/statement")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-31T23:59:59Z")
                        .param("type", "TRANSFER")
                        .param("sort", "createdAt,asc")
                        .header("Authorization", "Bearer " + token(userId, Instant.now().plusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].direction").value("OUT"))
                .andExpect(jsonPath("$.items[0].amount").value("25.50"))
                .andExpect(jsonPath("$.items[0].currency").value("BRL"))
                .andExpect(jsonPath("$.items[0].counterpartyName").value("Maria Souza"))
                .andExpect(jsonPath("$.items[0].description").value("Almoco"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-08-06T10:30:00Z"))
                .andExpect(jsonPath("$.items[0].completedAt").value("2026-08-06T10:30:00Z"));

        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.userId).isEqualTo(userId);
        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.query.page()).isZero();
        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.query.size()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.query.from())
                .isEqualTo("2026-08-01T00:00:00Z");
        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.query.to())
                .isEqualTo("2026-08-31T23:59:59Z");
        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.query.type()).isEqualTo("TRANSFER");
        org.assertj.core.api.Assertions.assertThat(getWalletStatementUseCase.query.sort()).isEqualTo("createdAt,asc");
    }

    @Test
    void shouldRejectStatementWithoutJwt() throws Exception {
        mockMvc.perform(get("/wallets/me/statement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectStatementWithExpiredJwt() throws Exception {
        mockMvc.perform(get("/wallets/me/statement")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().minusSeconds(1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnBadRequestForInvalidStatementQuery() throws Exception {
        getWalletStatementUseCase.exception = new InvalidStatementQueryException("sort invalido");

        mockMvc.perform(get("/wallets/me/statement")
                        .param("sort", "amount,desc")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("sort invalido"));
    }

    private static String token(UUID userId, Instant expiresAt) throws Exception {
        Instant issuedAt = expiresAt.minusSeconds(60);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        FakeGetWalletBalanceUseCase fakeGetWalletBalanceUseCase() {
            return new FakeGetWalletBalanceUseCase();
        }

        @Bean
        FakeGetWalletStatementUseCase fakeGetWalletStatementUseCase() {
            return new FakeGetWalletStatementUseCase();
        }
    }

    static class FakeGetWalletBalanceUseCase extends GetWalletBalanceUseCase {

        private UUID userId;
        private WalletBalanceResult result;
        private RuntimeException exception;

        FakeGetWalletBalanceUseCase() {
            super(null);
        }

        @Override
        public WalletBalanceResult getBalance(UUID userId) {
            this.userId = userId;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    static class FakeGetWalletStatementUseCase extends GetWalletStatementUseCase {

        private UUID userId;
        private StatementQuery query;
        private StatementResult result;
        private RuntimeException exception;

        FakeGetWalletStatementUseCase() {
            super(null, null);
        }

        @Override
        public StatementResult getStatement(UUID authenticatedUserId, StatementQuery query) {
            this.userId = authenticatedUserId;
            this.query = query;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }
}
