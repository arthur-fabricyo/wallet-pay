package ElFabrica.Wallet_pay.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.idempotency.IdempotencyService;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentOperation;
import ElFabrica.Wallet_pay.common.idempotency.RequestHash;
import ElFabrica.Wallet_pay.common.money.InvalidAmountException;
import ElFabrica.Wallet_pay.support.PassThroughIdempotencyService;
import ElFabrica.Wallet_pay.support.PermissiveOperationLimitService;
import ElFabrica.Wallet_pay.support.RecordingAuditLogger;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateTransferUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-06T10:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static CreateTransferUseCase useCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository
    ) {
        return useCase(walletRepository, transactionRepository, new PassThroughIdempotencyService());
    }

    private static CreateTransferUseCase useCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            IdempotencyService idempotencyService
    ) {
        return useCase(walletRepository, transactionRepository, idempotencyService, new RecordingAuditLogger());
    }

    private static CreateTransferUseCase useCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            IdempotencyService idempotencyService,
            AuditLogger auditLogger
    ) {
        return new CreateTransferUseCase(walletRepository, transactionRepository, CLOCK, idempotencyService, auditLogger, new PermissiveOperationLimitService());
    }

    @Test
    void shouldProtectTransferWithIdempotencyKeyAndNormalizedHash() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        FakeWalletRepository walletRepository = new FakeWalletRepository(Map.of(
                senderUserId, wallet(senderUserId, UUID.randomUUID(), "100.00"),
                receiverUserId, wallet(receiverUserId, UUID.randomUUID(), "10.00")
        ));
        PassThroughIdempotencyService idempotencyService = new PassThroughIdempotencyService();
        CreateTransferUseCase useCase = useCase(
                walletRepository.proxy(),
                new FakeTransactionRepository().proxy(),
                idempotencyService
        );

        useCase.transfer(new TransferCommand(senderUserId, receiverUserId, "25.5", " Almoco ", "chave-1"));

        assertThat(idempotencyService.lastRequest.userId()).isEqualTo(senderUserId);
        assertThat(idempotencyService.lastRequest.operation()).isEqualTo(IdempotentOperation.TRANSFER_CREATE);
        assertThat(idempotencyService.lastRequest.key()).isEqualTo("chave-1");
        assertThat(idempotencyService.lastRequest.requestHash())
                .isEqualTo(RequestHash.of(receiverUserId.toString(), "25.50", "Almoco"));
    }

    @Test
    void shouldTransferBalanceAndRegisterTransaction() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        WalletEntity senderWallet = wallet(senderUserId, senderWalletId, "100.00");
        WalletEntity receiverWallet = wallet(receiverUserId, receiverWalletId, "10.00");
        FakeWalletRepository walletRepository = new FakeWalletRepository(Map.of(
                senderUserId, senderWallet,
                receiverUserId, receiverWallet
        ));
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        CreateTransferUseCase useCase = useCase(
                walletRepository.proxy(),
                transactionRepository.proxy()
        );

        TransferResult result = useCase.transfer(new TransferCommand(
                senderUserId,
                receiverUserId,
                "25.50",
                " Almoco ",
                "chave-1"
        )).result();

        assertThat(senderWallet.getBalance()).isEqualByComparingTo("74.50");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("35.50");
        assertThat(transactionRepository.savedTransaction).isNotNull();
        assertThat(transactionRepository.savedTransaction.getSenderWallet()).isSameAs(senderWallet);
        assertThat(transactionRepository.savedTransaction.getReceiverWallet()).isSameAs(receiverWallet);
        assertThat(transactionRepository.savedTransaction.getDescription()).isEqualTo("Almoco");
        assertThat(result.transactionId()).isEqualTo(transactionRepository.transactionId);
        assertThat(result.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.amount()).isEqualTo("25.50");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.senderWalletId()).isEqualTo(senderWalletId);
        assertThat(result.receiverWalletId()).isEqualTo(receiverWalletId);
        assertThat(result.senderWallet().balance()).isEqualTo("74.50");
        assertThat(result.senderWallet().currency()).isEqualTo("BRL");
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.completedAt()).isNotNull();
        assertThat(transactionRepository.savedTransaction.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRecordTransferCreatedInTheSameTransaction() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID receiverWalletId = UUID.randomUUID();
        FakeWalletRepository walletRepository = new FakeWalletRepository(Map.of(
                senderUserId, wallet(senderUserId, senderWalletId, "100.00"),
                receiverUserId, wallet(receiverUserId, receiverWalletId, "10.00")
        ));
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        CreateTransferUseCase useCase = useCase(
                walletRepository.proxy(),
                transactionRepository.proxy(),
                new PassThroughIdempotencyService(),
                auditLogger
        );

        useCase.transfer(new TransferCommand(senderUserId, receiverUserId, "25.50", null, "chave-1"));

        assertThat(auditLogger.recorded).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.TRANSFER_CREATED);
            assertThat(entry.resourceId()).isEqualTo(transactionRepository.transactionId);
            assertThat(entry.actorUserId()).isEqualTo(senderUserId);
            assertThat(entry.metadata())
                    .containsEntry("amount", "25.50")
                    .containsEntry("currency", "BRL")
                    .containsEntry("senderWalletId", senderWalletId.toString())
                    .containsEntry("receiverWalletId", receiverWalletId.toString());
        });
    }

    @Test
    void shouldRejectAmountWithMoreThanTwoDecimalPlaces() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        FakeWalletRepository walletRepository = new FakeWalletRepository(Map.of(
                senderUserId, wallet(senderUserId, UUID.randomUUID(), "100.00"),
                receiverUserId, wallet(receiverUserId, UUID.randomUUID(), "10.00")
        ));
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        CreateTransferUseCase useCase = useCase(walletRepository.proxy(), transactionRepository.proxy());

        assertThatThrownBy(() -> useCase.transfer(new TransferCommand(
                senderUserId,
                receiverUserId,
                "10.123",
                null,
                "chave-1"
        ))).isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor deve ter no maximo duas casas decimais");

        assertThat(transactionRepository.savedTransaction).isNull();
    }

    @Test
    void shouldRejectInsufficientBalanceWithoutChangingBalances() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        WalletEntity senderWallet = wallet(senderUserId, UUID.randomUUID(), "20.00");
        WalletEntity receiverWallet = wallet(receiverUserId, UUID.randomUUID(), "10.00");
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        CreateTransferUseCase useCase = useCase(
                new FakeWalletRepository(Map.of(senderUserId, senderWallet, receiverUserId, receiverWallet)).proxy(),
                transactionRepository.proxy()
        );

        assertThatThrownBy(() -> useCase.transfer(new TransferCommand(
                senderUserId,
                receiverUserId,
                "25.50",
                null,
                "chave-1"
        ))).isInstanceOf(InsufficientBalanceException.class);

        assertThat(senderWallet.getBalance()).isEqualByComparingTo("20.00");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("10.00");
        assertThat(transactionRepository.savedTransaction).isNull();
    }

    @Test
    void shouldRejectTransferToSameUser() {
        UUID userId = UUID.randomUUID();
        FakeWalletRepository walletRepository = new FakeWalletRepository(Map.of());
        CreateTransferUseCase useCase = useCase(
                walletRepository.proxy(),
                new FakeTransactionRepository().proxy()
        );

        assertThatThrownBy(() -> useCase.transfer(new TransferCommand(
                userId,
                userId,
                "10.00",
                null,
                "chave-1"
        ))).isInstanceOf(InvalidTransferException.class);

        assertThat(walletRepository.findByUserIdCalls).isEqualTo(0);
    }

    private static WalletEntity wallet(UUID userId, UUID walletId, String balance) {
        UserEntity user = new UserEntity("Joao Silva", userId + "@email.com", "hash", "52998224725");
        setField(user, "id", userId);
        WalletEntity wallet = new WalletEntity(user);
        setField(wallet, "id", walletId);
        setField(wallet, "balance", new BigDecimal(balance));
        setField(wallet, "updatedAt", Instant.parse("2026-08-06T10:30:00Z"));
        return wallet;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeWalletRepository {

        private final Map<UUID, WalletEntity> wallets;
        private int findByUserIdCalls;

        private FakeWalletRepository(Map<UUID, WalletEntity> wallets) {
            this.wallets = new HashMap<>(wallets);
        }

        private WalletRepository proxy() {
            return (WalletRepository) Proxy.newProxyInstance(
                    WalletRepository.class.getClassLoader(),
                    new Class<?>[] {WalletRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByUserId" -> {
                            findByUserIdCalls++;
                            yield Optional.ofNullable(wallets.get((UUID) args[0]));
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeTransactionRepository {

        private final UUID transactionId = UUID.randomUUID();
        private TransactionEntity savedTransaction;

        private TransactionRepository proxy() {
            return (TransactionRepository) Proxy.newProxyInstance(
                    TransactionRepository.class.getClassLoader(),
                    new Class<?>[] {TransactionRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "saveAndFlush" -> {
                            savedTransaction = (TransactionEntity) args[0];
                            setField(savedTransaction, "id", transactionId);
                            yield savedTransaction;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
