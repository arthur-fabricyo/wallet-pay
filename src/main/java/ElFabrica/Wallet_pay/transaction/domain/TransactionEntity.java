package ElFabrica.Wallet_pay.transaction.domain;

import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_wallet_id")
    private WalletEntity senderWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_wallet_id", nullable = false)
    private WalletEntity receiverWallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected TransactionEntity() {
    }

    private TransactionEntity(
            TransactionType type,
            WalletEntity senderWallet,
            WalletEntity receiverWallet,
            BigDecimal amount,
            String description,
            Instant occurredAt
    ) {
        this.type = type;
        this.senderWallet = senderWallet;
        this.receiverWallet = receiverWallet;
        this.status = TransactionStatus.COMPLETED;
        this.amount = amount;
        this.currency = WalletEntity.DEFAULT_CURRENCY;
        this.description = description;
        this.createdAt = occurredAt;
        this.completedAt = occurredAt;
    }

    public static TransactionEntity completedTransfer(
            WalletEntity senderWallet,
            WalletEntity receiverWallet,
            BigDecimal amount,
            String description,
            Instant occurredAt
    ) {
        return new TransactionEntity(TransactionType.TRANSFER, senderWallet, receiverWallet, amount, description, occurredAt);
    }

    public static TransactionEntity completedDeposit(
            WalletEntity receiverWallet,
            BigDecimal amount,
            String description,
            Instant occurredAt
    ) {
        return new TransactionEntity(TransactionType.DEPOSIT, null, receiverWallet, amount, description, occurredAt);
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.completedAt == null && this.status == TransactionStatus.COMPLETED) {
            this.completedAt = this.createdAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public WalletEntity getSenderWallet() {
        return senderWallet;
    }

    public WalletEntity getReceiverWallet() {
        return receiverWallet;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
