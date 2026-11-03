package ElFabrica.Wallet_pay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.TestUser;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferService;
import ElFabrica.Wallet_pay.wallet.domain.WalletLimitEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletLimitRepository;
import ElFabrica.Wallet_pay.wallet.service.BalanceConflictException;
import ElFabrica.Wallet_pay.wallet.service.LimitExceededException;
import ElFabrica.Wallet_pay.wallet.service.LimitPeriods;
import ElFabrica.Wallet_pay.wallet.service.OperationLimitService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class LimitsIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private OperationLimitService operationLimitService;

    @Autowired
    private WalletLimitRepository walletLimitRepository;

    @Autowired
    private TransferService transferService;

    @Test
    void shouldRejectTransferAbovePerOperationLimit() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver, "5000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Limite por operacao de transferencia excedido"))
                .andExpect(jsonPath("$.fields.limitType").value("PER_OPERATION"))
                .andExpect(jsonPath("$.fields.limit").value("5000.00"))
                .andExpect(jsonPath("$.fields.available").value("5000.00"));
        assertThat(balanceOf(sender)).isEqualByComparingTo("20000.00");
    }

    @Test
    void shouldRejectTransferAboveDailyLimit() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver, "5000.00").andExpect(status().isCreated());
        transfer(sender, receiver, "5000.00").andExpect(status().isCreated());
        transfer(sender, receiver, "0.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Limite diario de transferencia excedido"))
                .andExpect(jsonPath("$.fields.limitType").value("DAILY"))
                .andExpect(jsonPath("$.fields.limit").value("10000.00"))
                .andExpect(jsonPath("$.fields.available").value("0.00"));
    }

    @Test
    void shouldRejectTransferAboveMonthlyLimitUsingTheSaoPauloCalendar() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        insertTransfer(sender, receiver, "46000.00", Instant.parse("2026-10-02T15:00:00Z"), "COMPLETED");
        insertTransfer(sender, receiver, "5000.00", saoPaulo("2026-09-30T23:00"), "COMPLETED");

        transfer(sender, receiver, "4000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields.limitType").value("MONTHLY"))
                .andExpect(jsonPath("$.fields.limit").value("50000.00"))
                .andExpect(jsonPath("$.fields.available").value("4000.00"));
        transfer(sender, receiver, "4000.00").andExpect(status().isCreated());
    }

    @Test
    void shouldApplyNightPerOperationLimit() throws Exception {
        useSaoPauloTime("2026-10-15T23:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver, "1000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Limite noturno por operacao de transferencia excedido"))
                .andExpect(jsonPath("$.fields.limitType").value("NIGHT_PER_OPERATION"))
                .andExpect(jsonPath("$.fields.limit").value("1000.00"));
        transfer(sender, receiver, "1000.00").andExpect(status().isCreated());
    }

    @Test
    void shouldCountTransfersFromYesterdayEveningInTheNightWindow() throws Exception {
        useSaoPauloTime("2026-10-16T02:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        insertTransfer(sender, receiver, "900.00", saoPaulo("2026-10-15T21:00"), "COMPLETED");
        insertTransfer(sender, receiver, "500.00", saoPaulo("2026-10-15T19:00"), "COMPLETED");

        transfer(sender, receiver, "100.00").andExpect(status().isCreated());
        transfer(sender, receiver, "0.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Limite noturno de transferencia excedido"))
                .andExpect(jsonPath("$.fields.limitType").value("NIGHT_WINDOW"))
                .andExpect(jsonPath("$.fields.available").value("0.00"));
    }

    @Test
    void shouldApplyCustomLimitLowerThanTheGlobalOne() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        transactionTemplate.executeWithoutResult(status -> {
            WalletLimitEntity limits = new WalletLimitEntity(sender.walletId());
            limits.update(new BigDecimal("2000.00"), null, null, null, null, Instant.now());
            walletLimitRepository.save(limits);
        });

        transfer(sender, receiver, "2000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields.limitType").value("PER_OPERATION"))
                .andExpect(jsonPath("$.fields.limit").value("2000.00"));
        transfer(sender, receiver, "2000.00").andExpect(status().isCreated());
    }

    @Test
    void shouldApplyGlobalDepositLimits() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser user = createVerifiedUser("Joao Silva", "0.00");

        deposit(user, "10000.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Limite por operacao de deposito excedido"))
                .andExpect(jsonPath("$.fields.limitType").value("PER_OPERATION"));
        deposit(user, "10000.00").andExpect(status().isCreated());
        deposit(user, "10000.00").andExpect(status().isCreated());
        deposit(user, "0.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Limite diario de deposito excedido"))
                .andExpect(jsonPath("$.fields.limitType").value("DAILY"))
                .andExpect(jsonPath("$.fields.available").value("0.00"));
    }

    @Test
    void usageShouldIgnoreFailedTransactionsOtherTypesAndOtherWallets() {
        Instant now = useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "0.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        TestUser other = createVerifiedUser("Ana Lima", "0.00");
        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        insertTransfer(sender, receiver, "3000.00", oneHourAgo, "FAILED");
        insertDeposit(sender, "3000.00", oneHourAgo);
        insertTransfer(other, receiver, "3000.00", oneHourAgo, "COMPLETED");
        insertTransfer(sender, receiver, "100.00", oneHourAgo, "COMPLETED");
        Instant dayStart = LimitPeriods.day(now, SAO_PAULO).start();

        assertThat(operationLimitService.sentTransfers(sender.walletId(), dayStart, now)).isEqualByComparingTo("100.00");
        assertThat(operationLimitService.receivedDeposits(sender.walletId(), dayStart, now)).isEqualByComparingTo("3000.00");
    }

    @Test
    void concurrentTransfersMustNotExceedTheDailyLimitTogether() throws Exception {
        Instant now = useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");
        insertTransfer(sender, receiver, "4000.00", now.minus(Duration.ofHours(1)), "COMPLETED");

        List<String> outcomes = runConcurrently(2, () -> transferAsClient(sender, receiver, "5000.00"));

        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "LIMIT_EXCEEDED");
        assertThat(balanceOf(sender)).isEqualByComparingTo("15000.00");
    }

    @Test
    void shouldAuditLimitRejectionWithTheLimitType() throws Exception {
        useSaoPauloTime("2026-10-15T15:00");
        TestUser sender = createVerifiedUser("Joao Silva", "20000.00");
        TestUser receiver = createVerifiedUser("Maria Souza", "0.00");

        transfer(sender, receiver, "5000.01").andExpect(status().isUnprocessableEntity());

        assertThat(jdbcTemplate.queryForObject(
                "select failure_code from audit_events where action = 'TRANSFER_REJECTED'", String.class
        )).isEqualTo("LIMIT_EXCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "select metadata->>'limitType' from audit_events where action = 'TRANSFER_REJECTED'", String.class
        )).isEqualTo("PER_OPERATION");
    }

    private String transferAsClient(TestUser sender, TestUser receiver, String amount) {
        String idempotencyKey = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                transferService.transfer(new TransferCommand(sender.userId(), receiver.userId(), amount, null, idempotencyKey));
                return "SUCCESS";
            } catch (LimitExceededException exception) {
                return "LIMIT_EXCEEDED";
            } catch (BalanceConflictException exception) {
                // o cliente repete depois de 409 BALANCE_CONFLICT
            }
        }
        throw new AssertionError("Transferencia nao terminou depois de 50 tentativas do cliente");
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

    private void insertTransfer(TestUser sender, TestUser receiver, String amount, Instant createdAt, String status) {
        jdbcTemplate.update("""
                insert into transactions (
                    id, sender_wallet_id, receiver_wallet_id, type, status, amount, currency, created_at, completed_at
                ) values (?, ?, ?, 'TRANSFER', ?, ?, 'BRL', ?, ?)
                """,
                UUID.randomUUID(),
                sender.walletId(),
                receiver.walletId(),
                status,
                new BigDecimal(amount),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void insertDeposit(TestUser user, String amount, Instant createdAt) {
        jdbcTemplate.update("""
                insert into transactions (
                    id, sender_wallet_id, receiver_wallet_id, type, status, amount, currency, created_at, completed_at
                ) values (?, null, ?, 'DEPOSIT', 'COMPLETED', ?, 'BRL', ?, ?)
                """,
                UUID.randomUUID(),
                user.walletId(),
                new BigDecimal(amount),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private static Instant saoPaulo(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SAO_PAULO).toInstant();
    }
}
