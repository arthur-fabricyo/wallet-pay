package ElFabrica.Wallet_pay.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.common.idempotency.IdempotencyService;
import ElFabrica.Wallet_pay.common.money.InvalidAmountException;
import ElFabrica.Wallet_pay.support.PassThroughIdempotencyService;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateDepositUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-15T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldCreditOwnWalletAndRegisterDeposit() {
        UUID userId = UUID.randomUUID();
        WalletEntity wallet = wallet(userId, "100.00");
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        CreateDepositUseCase useCase = useCase(new FakeWalletRepository(wallet).proxy(), transactionRepository.proxy());

        DepositResult result = useCase.deposit(new DepositCommand(userId, "150", "  Aporte inicial ", "chave-1")).result();

        assertThat(wallet.getBalance()).isEqualByComparingTo("250.00");
        TransactionEntity saved = transactionRepository.savedTransaction;
        assertThat(saved.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(saved.getSenderWallet()).isNull();
        assertThat(saved.getReceiverWallet()).isSameAs(wallet);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.transactionId()).isEqualTo(transactionRepository.transactionId);
        assertThat(result.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.amount()).isEqualTo("150.00");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.description()).isEqualTo("Aporte inicial");
        assertThat(result.wallet().id()).isEqualTo(wallet.getId());
        assertThat(result.wallet().balance()).isEqualTo("250.00");
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.completedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectInvalidAmountWithoutTouchingWallet() {
        UUID userId = UUID.randomUUID();
        WalletEntity wallet = wallet(userId, "100.00");
        FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
        CreateDepositUseCase useCase = useCase(new FakeWalletRepository(wallet).proxy(), transactionRepository.proxy());

        assertThatThrownBy(() -> useCase.deposit(new DepositCommand(userId, "0", null, "chave-1")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor deve ser maior que zero");
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.savedTransaction).isNull();
    }

    @Test
    void shouldFailWhenWalletDoesNotExist() {
        CreateDepositUseCase useCase = useCase(
                new FakeWalletRepository(null).proxy(),
                new FakeTransactionRepository().proxy()
        );

        assertThatThrownBy(() -> useCase.deposit(new DepositCommand(UUID.randomUUID(), "10.00", null, "chave-1")))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessage("Carteira nao encontrada para o usuario autenticado");
    }

    private static CreateDepositUseCase useCase(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        return useCase(walletRepository, transactionRepository, new PassThroughIdempotencyService());
    }

    private static CreateDepositUseCase useCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            IdempotencyService idempotencyService
    ) {
        return new CreateDepositUseCase(walletRepository, transactionRepository, CLOCK, idempotencyService);
    }

    private static WalletEntity wallet(UUID userId, String balance) {
        UserEntity user = new UserEntity("Joao Silva", userId + "@email.com", "hash", "52998224725");
        setField(user, "id", userId);
        WalletEntity wallet = new WalletEntity(user);
        setField(wallet, "id", UUID.randomUUID());
        setField(wallet, "balance", new BigDecimal(balance));
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
