package ElFabrica.Wallet_pay.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class GetWalletStatementUseCaseTest {

    @Test
    void shouldReturnStatementEntriesWithDirectionAndCounterparty() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        WalletEntity ownWallet = wallet(userId, walletId, "Joao Silva");
        WalletEntity receiverWallet = wallet(UUID.randomUUID(), UUID.randomUUID(), "Maria Souza");
        Instant now = Instant.parse("2026-08-06T10:30:00Z");
        TransactionEntity transaction = transaction(UUID.randomUUID(), ownWallet, receiverWallet, now);
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository(List.of(transaction), 1);
        GetWalletStatementUseCase useCase = new GetWalletStatementUseCase(
                new FakeWalletRepository(ownWallet).proxy(),
                transactionRepository.proxy()
        );

        StatementResult result = useCase.getStatement(userId, new StatementQuery(
                0,
                20,
                "2026-08-01T00:00:00Z",
                "2026-08-31T23:59:59Z",
                "TRANSFER",
                "createdAt,asc"
        ));

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        StatementEntryResult item = result.items().get(0);
        assertThat(item.transactionId()).isEqualTo(transaction.getId());
        assertThat(item.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(item.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(item.direction()).isEqualTo(StatementDirection.OUT);
        assertThat(item.amount()).isEqualTo("25.50");
        assertThat(item.currency()).isEqualTo("BRL");
        assertThat(item.counterpartyName()).isEqualTo("Maria Souza");
        assertThat(item.description()).isEqualTo("Almoco");
        assertThat(item.createdAt()).isEqualTo(now);
        assertThat(item.completedAt()).isEqualTo(now);
        assertThat(transactionRepository.walletId).isEqualTo(walletId);
        assertThat(transactionRepository.from).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(transactionRepository.to).isEqualTo(Instant.parse("2026-08-31T23:59:59Z"));
        assertThat(transactionRepository.type).isEqualTo(TransactionType.TRANSFER);
        assertThat(transactionRepository.pageable.getPageNumber()).isZero();
        assertThat(transactionRepository.pageable.getPageSize()).isEqualTo(20);
        assertThat(transactionRepository.pageable.getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    void shouldReturnIncomingDirectionForReceiverWallet() {
        UUID userId = UUID.randomUUID();
        WalletEntity senderWallet = wallet(UUID.randomUUID(), UUID.randomUUID(), "Joao Silva");
        WalletEntity ownWallet = wallet(userId, UUID.randomUUID(), "Maria Souza");
        TransactionEntity transaction = transaction(UUID.randomUUID(), senderWallet, ownWallet, Instant.now());
        GetWalletStatementUseCase useCase = new GetWalletStatementUseCase(
                new FakeWalletRepository(ownWallet).proxy(),
                new FakeTransactionRepository(List.of(transaction), 1).proxy()
        );

        StatementResult result = useCase.getStatement(userId, new StatementQuery(0, 20, null, null, null, null));

        assertThat(result.items().get(0).direction()).isEqualTo(StatementDirection.IN);
        assertThat(result.items().get(0).counterpartyName()).isEqualTo("Joao Silva");
    }

    @Test
    void shouldUseDefaultPaginationAndSort() {
        WalletEntity wallet = wallet(UUID.randomUUID(), UUID.randomUUID(), "Joao Silva");
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository(List.of(), 0);
        GetWalletStatementUseCase useCase = new GetWalletStatementUseCase(
                new FakeWalletRepository(wallet).proxy(),
                transactionRepository.proxy()
        );

        useCase.getStatement(wallet.getUser().getId(), new StatementQuery(0, 20, null, null, null, null));

        assertThat(transactionRepository.pageable.getPageNumber()).isZero();
        assertThat(transactionRepository.pageable.getPageSize()).isEqualTo(20);
        assertThat(transactionRepository.pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void shouldRejectInvalidQueryValues() {
        WalletEntity wallet = wallet(UUID.randomUUID(), UUID.randomUUID(), "Joao Silva");
        GetWalletStatementUseCase useCase = new GetWalletStatementUseCase(
                new FakeWalletRepository(wallet).proxy(),
                new FakeTransactionRepository(List.of(), 0).proxy()
        );

        assertThatThrownBy(() -> useCase.getStatement(wallet.getUser().getId(),
                new StatementQuery(-1, 20, null, null, null, "createdAt,desc")))
                .isInstanceOf(InvalidStatementQueryException.class);
        assertThatThrownBy(() -> useCase.getStatement(wallet.getUser().getId(),
                new StatementQuery(0, 101, null, null, null, "createdAt,desc")))
                .isInstanceOf(InvalidStatementQueryException.class);
        assertThatThrownBy(() -> useCase.getStatement(wallet.getUser().getId(),
                new StatementQuery(0, 20, "2026-08-31T00:00:00Z", "2026-08-01T00:00:00Z", null, "createdAt,desc")))
                .isInstanceOf(InvalidStatementQueryException.class);
        assertThatThrownBy(() -> useCase.getStatement(wallet.getUser().getId(),
                new StatementQuery(0, 20, null, null, "INVALID", "createdAt,desc")))
                .isInstanceOf(InvalidStatementQueryException.class);
        assertThatThrownBy(() -> useCase.getStatement(wallet.getUser().getId(),
                new StatementQuery(0, 20, null, null, null, "amount,desc")))
                .isInstanceOf(InvalidStatementQueryException.class);
    }

    @Test
    void shouldReturnNotFoundWhenAuthenticatedUserHasNoWallet() {
        GetWalletStatementUseCase useCase = new GetWalletStatementUseCase(
                new FakeWalletRepository(null).proxy(),
                new FakeTransactionRepository(List.of(), 0).proxy()
        );

        assertThatThrownBy(() -> useCase.getStatement(UUID.randomUUID(), new StatementQuery(0, 20, null, null, null, null)))
                .isInstanceOf(WalletNotFoundException.class);
    }

    private static TransactionEntity transaction(
            UUID transactionId,
            WalletEntity senderWallet,
            WalletEntity receiverWallet,
            Instant now
    ) {
        TransactionEntity transaction = TransactionEntity.completedTransfer(
                senderWallet,
                receiverWallet,
                new BigDecimal("25.50"),
                "Almoco"
        );
        setField(transaction, "id", transactionId);
        setField(transaction, "createdAt", now);
        setField(transaction, "completedAt", now);
        return transaction;
    }

    private static WalletEntity wallet(UUID userId, UUID walletId, String name) {
        UserEntity user = new UserEntity(name, userId + "@email.com", "hash", "52998224725");
        setField(user, "id", userId);
        WalletEntity wallet = new WalletEntity(user);
        setField(wallet, "id", walletId);
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

        private final WalletEntity wallet;

        private FakeWalletRepository(WalletEntity wallet) {
            this.wallet = wallet;
        }

        private WalletRepository proxy() {
            return (WalletRepository) Proxy.newProxyInstance(
                    WalletRepository.class.getClassLoader(),
                    new Class<?>[] {WalletRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByUserId" -> Optional.ofNullable(wallet);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeTransactionRepository {

        private final List<TransactionEntity> transactions;
        private final long totalElements;
        private UUID walletId;
        private Instant from;
        private Instant to;
        private TransactionType type;
        private Pageable pageable;

        private FakeTransactionRepository(List<TransactionEntity> transactions, long totalElements) {
            this.transactions = transactions;
            this.totalElements = totalElements;
        }

        private TransactionRepository proxy() {
            return (TransactionRepository) Proxy.newProxyInstance(
                    TransactionRepository.class.getClassLoader(),
                    new Class<?>[] {TransactionRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findStatementTransactions" -> {
                            walletId = (UUID) args[0];
                            from = (Instant) args[1];
                            to = (Instant) args[2];
                            type = (TransactionType) args[3];
                            pageable = (Pageable) args[4];
                            yield new PageImpl<>(transactions, PageRequest.of(
                                    pageable.getPageNumber(),
                                    pageable.getPageSize(),
                                    pageable.getSort()
                            ), totalElements);
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
