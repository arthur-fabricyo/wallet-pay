package ElFabrica.Wallet_pay.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetReceiptUseCaseTest {

    @Test
    void shouldReturnReceiptForSender() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T10:30:00Z");
        TransactionEntity transaction = transaction(
                transactionId,
                wallet(senderUserId, "Joao Silva", "52998224725"),
                wallet(receiverUserId, "Maria Souza", "12345678901234"),
                now
        );
        GetReceiptUseCase useCase = new GetReceiptUseCase(new FakeTransactionRepository(transaction).proxy());

        ReceiptResult result = useCase.getReceipt(senderUserId, transactionId);

        assertThat(result.receiptId()).isEqualTo(transactionId);
        assertThat(result.transactionId()).isEqualTo(transactionId);
        assertThat(result.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.amount()).isEqualTo("25.50");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.senderName()).isEqualTo("Joao Silva");
        assertThat(result.senderDocumentMasked()).isEqualTo("***982247**");
        assertThat(result.receiverName()).isEqualTo("Maria Souza");
        assertThat(result.receiverDocumentMasked()).isEqualTo("****567890****");
        assertThat(result.description()).isEqualTo("Almoco");
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.completedAt()).isEqualTo(now);
    }

    @Test
    void shouldReturnReceiptForReceiver() {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        TransactionEntity transaction = transaction(
                transactionId,
                wallet(senderUserId, "Joao Silva", "52998224725"),
                wallet(receiverUserId, "Maria Souza", "12345678901234"),
                Instant.parse("2026-08-06T10:30:00Z")
        );
        GetReceiptUseCase useCase = new GetReceiptUseCase(new FakeTransactionRepository(transaction).proxy());

        ReceiptResult result = useCase.getReceipt(receiverUserId, transactionId);

        assertThat(result.transactionId()).isEqualTo(transactionId);
    }

    @Test
    void shouldRejectReceiptForUserThatDoesNotParticipateInTransaction() {
        TransactionEntity transaction = transaction(
                UUID.randomUUID(),
                wallet(UUID.randomUUID(), "Joao Silva", "52998224725"),
                wallet(UUID.randomUUID(), "Maria Souza", "12345678901234"),
                Instant.parse("2026-08-06T10:30:00Z")
        );
        GetReceiptUseCase useCase = new GetReceiptUseCase(new FakeTransactionRepository(transaction).proxy());

        assertThatThrownBy(() -> useCase.getReceipt(UUID.randomUUID(), transaction.getId()))
                .isInstanceOf(ReceiptAccessDeniedException.class);
    }

    @Test
    void shouldReturnNotFoundWhenTransactionDoesNotExist() {
        GetReceiptUseCase useCase = new GetReceiptUseCase(new FakeTransactionRepository(null).proxy());

        assertThatThrownBy(() -> useCase.getReceipt(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void shouldRejectReceiptWhenTransactionIsNotCompleted() {
        UUID senderUserId = UUID.randomUUID();
        TransactionEntity transaction = transaction(
                UUID.randomUUID(),
                wallet(senderUserId, "Joao Silva", "52998224725"),
                wallet(UUID.randomUUID(), "Maria Souza", "12345678901234"),
                Instant.parse("2026-08-06T10:30:00Z")
        );
        setField(transaction, "status", TransactionStatus.FAILED);
        setField(transaction, "completedAt", null);

        GetReceiptUseCase useCase = new GetReceiptUseCase(new FakeTransactionRepository(transaction).proxy());

        assertThatThrownBy(() -> useCase.getReceipt(senderUserId, transaction.getId()))
                .isInstanceOf(ReceiptUnavailableException.class);
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

    private static WalletEntity wallet(UUID userId, String name, String document) {
        UserEntity user = new UserEntity(name, userId + "@email.com", "hash", document);
        setField(user, "id", userId);
        WalletEntity wallet = new WalletEntity(user);
        setField(wallet, "id", UUID.randomUUID());
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

    private static final class FakeTransactionRepository {

        private final TransactionEntity transaction;

        private FakeTransactionRepository(TransactionEntity transaction) {
            this.transaction = transaction;
        }

        private TransactionRepository proxy() {
            return (TransactionRepository) Proxy.newProxyInstance(
                    TransactionRepository.class.getClassLoader(),
                    new Class<?>[] {TransactionRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findReceiptById" -> Optional.ofNullable(transaction);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
