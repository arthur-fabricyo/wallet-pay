package ElFabrica.Wallet_pay.transaction.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.config.SecurityConfig;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.transaction.service.TransferService;
import ElFabrica.Wallet_pay.transaction.service.GetReceiptUseCase;
import ElFabrica.Wallet_pay.transaction.service.InsufficientBalanceException;
import ElFabrica.Wallet_pay.transaction.service.ReceiptAccessDeniedException;
import ElFabrica.Wallet_pay.transaction.service.ReceiptResult;
import ElFabrica.Wallet_pay.transaction.service.ReceiptUnavailableException;
import ElFabrica.Wallet_pay.transaction.service.TransactionNotFoundException;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferResult;
import ElFabrica.Wallet_pay.transaction.service.TransferSenderWalletResult;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import ElFabrica.Wallet_pay.wallet.service.WalletNotFoundException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, TransactionControllerTest.TestBeans.class})
@TestPropertySource(properties = "wallet-pay.auth.jwt-secret=wallet-pay-test-secret-with-32-bytes")
class TransactionControllerTest {

    private static final String SECRET = "wallet-pay-test-secret-with-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeTransferService transferService;

    @Autowired
    private FakeGetReceiptUseCase getReceiptUseCase;

    @BeforeEach
    void setUp() {
        transferService.command = null;
        transferService.result = null;
        transferService.exception = null;
        transferService.replayed = false;
        getReceiptUseCase.authenticatedUserId = null;
        getReceiptUseCase.transactionId = null;
        getReceiptUseCase.result = null;
        getReceiptUseCase.exception = null;
    }

    @Test
    void shouldCreateAuthenticatedTransfer() throws Exception {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:30:00Z");
        transferService.result = new TransferResult(
                transactionId,
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                "25.50",
                "BRL",
                senderWalletId,
                receiverWalletId,
                new TransferSenderWalletResult(senderWalletId, "74.50", "BRL", now),
                now,
                now
        );

        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(senderUserId, Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50",
                                  "description": "Almoco"
                                }
                                """.formatted(receiverUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("25.50"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.senderWalletId").value(senderWalletId.toString()))
                .andExpect(jsonPath("$.receiverWalletId").value(receiverWalletId.toString()))
                .andExpect(jsonPath("$.senderWallet.id").value(senderWalletId.toString()))
                .andExpect(jsonPath("$.senderWallet.balance").value("74.50"))
                .andExpect(jsonPath("$.senderWallet.currency").value("BRL"))
                .andExpect(jsonPath("$.senderWallet.updatedAt").value("2026-08-06T10:30:00Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-06T10:30:00Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-08-06T10:30:00Z"));

        assertThat(transferService.command.senderUserId()).isEqualTo(senderUserId);
        assertThat(transferService.command.receiverUserId()).isEqualTo(receiverUserId);
        assertThat(transferService.command.idempotencyKey()).isEqualTo("chave-1");
        assertThat(transferService.command.amount()).isEqualTo("25.50");
        assertThat(transferService.command.description()).isEqualTo("Almoco");
    }

    @Test
    void shouldMarkReplayedTransfer() throws Exception {
        UUID senderWalletId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:30:00Z");
        transferService.result = new TransferResult(
                UUID.randomUUID(),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                "25.50",
                "BRL",
                senderWalletId,
                UUID.randomUUID(),
                new TransferSenderWalletResult(senderWalletId, "74.50", "BRL", now),
                now,
                now
        );
        transferService.replayed = true;

        mockMvc.perform(post("/transactions/transfers")
                        .header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void shouldRejectTransferWithoutJwt() throws Exception {
        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTransferWithExpiredJwt() throws Exception {
        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().minusSeconds(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidPayload() throws Exception {
        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.receiverUserId").exists())
                .andExpect(jsonPath("$.fields.amount").exists());
    }

    @Test
    void shouldReturnNotFoundWhenWalletIsMissing() throws Exception {
        transferService.exception = new WalletNotFoundException("Carteira de destino nao encontrada");

        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnUnprocessableEntityForInsufficientBalance() throws Exception {
        transferService.exception = new InsufficientBalanceException("Saldo insuficiente");

        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    void shouldReturnConflictWhenConcurrencyRetriesAreExhausted() throws Exception {
        transferService.exception = new BalanceConflictException("Operacao concorrente na carteira, tente novamente");

        mockMvc.perform(post("/transactions/transfers").header("Idempotency-Key", "chave-1")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverUserId": "%s",
                                  "amount": "25.50"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BALANCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Operacao concorrente na carteira, tente novamente"));
    }

    @Test
    void shouldReturnAuthenticatedUserReceipt() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:30:00Z");
        getReceiptUseCase.result = new ReceiptResult(
                transactionId,
                transactionId,
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                "25.50",
                "BRL",
                "Joao Silva",
                "***982247**",
                "Maria Souza",
                "**345678****",
                "Almoco",
                now,
                now
        );

        mockMvc.perform(get("/transactions/{transactionId}/receipt", transactionId)
                        .header("Authorization", "Bearer " + token(authenticatedUserId, Instant.now().plusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value(transactionId.toString()))
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("25.50"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.senderName").value("Joao Silva"))
                .andExpect(jsonPath("$.senderDocumentMasked").value("***982247**"))
                .andExpect(jsonPath("$.receiverName").value("Maria Souza"))
                .andExpect(jsonPath("$.receiverDocumentMasked").value("**345678****"))
                .andExpect(jsonPath("$.description").value("Almoco"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-06T10:30:00Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-08-06T10:30:00Z"));

        assertThat(getReceiptUseCase.authenticatedUserId).isEqualTo(authenticatedUserId);
        assertThat(getReceiptUseCase.transactionId).isEqualTo(transactionId);
    }

    @Test
    void shouldRejectReceiptWithoutJwt() throws Exception {
        mockMvc.perform(get("/transactions/{transactionId}/receipt", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenUserDoesNotParticipateInReceiptTransaction() throws Exception {
        getReceiptUseCase.exception = new ReceiptAccessDeniedException("Usuario nao participa da transacao");

        mockMvc.perform(get("/transactions/{transactionId}/receipt", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void shouldReturnNotFoundWhenReceiptTransactionDoesNotExist() throws Exception {
        getReceiptUseCase.exception = new TransactionNotFoundException("Transacao nao encontrada");

        mockMvc.perform(get("/transactions/{transactionId}/receipt", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldReturnConflictWhenReceiptTransactionIsNotCompleted() throws Exception {
        getReceiptUseCase.exception = new ReceiptUnavailableException("Comprovante disponivel apenas para transacoes concluidas");

        mockMvc.perform(get("/transactions/{transactionId}/receipt", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
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
        FakeTransferService fakeTransferService() {
            return new FakeTransferService();
        }

        @Bean
        FakeGetReceiptUseCase fakeGetReceiptUseCase() {
            return new FakeGetReceiptUseCase();
        }
    }

    static class FakeTransferService extends TransferService {

        private TransferCommand command;
        private TransferResult result;
        private boolean replayed;
        private RuntimeException exception;

        FakeTransferService() {
            super(null, null);
        }

        @Override
        public IdempotentResult<TransferResult> transfer(TransferCommand command) {
            this.command = command;
            if (exception != null) {
                throw exception;
            }
            return new IdempotentResult<>(result, replayed);
        }
    }

    static class FakeGetReceiptUseCase extends GetReceiptUseCase {

        private UUID authenticatedUserId;
        private UUID transactionId;
        private ReceiptResult result;
        private RuntimeException exception;

        FakeGetReceiptUseCase() {
            super(null);
        }

        @Override
        public ReceiptResult getReceipt(UUID authenticatedUserId, UUID transactionId) {
            this.authenticatedUserId = authenticatedUserId;
            this.transactionId = transactionId;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }
}
