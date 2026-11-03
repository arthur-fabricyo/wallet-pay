package ElFabrica.Wallet_pay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Table(name = "wallet_limits")
public class WalletLimitEntity {

    @Id
    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "transfer_per_operation", precision = 19, scale = 2)
    private BigDecimal transferPerOperation;

    @Column(name = "transfer_daily", precision = 19, scale = 2)
    private BigDecimal transferDaily;

    @Column(name = "transfer_monthly", precision = 19, scale = 2)
    private BigDecimal transferMonthly;

    @Column(name = "transfer_night_per_operation", precision = 19, scale = 2)
    private BigDecimal transferNightPerOperation;

    @Column(name = "transfer_night_window", precision = 19, scale = 2)
    private BigDecimal transferNightWindow;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletLimitEntity() {
    }

    public WalletLimitEntity(UUID walletId) {
        this.walletId = walletId;
    }

    public void update(
            BigDecimal perOperation,
            BigDecimal daily,
            BigDecimal monthly,
            BigDecimal nightPerOperation,
            BigDecimal nightWindow,
            Instant now
    ) {
        this.transferPerOperation = perOperation;
        this.transferDaily = daily;
        this.transferMonthly = monthly;
        this.transferNightPerOperation = nightPerOperation;
        this.transferNightWindow = nightWindow;
        this.updatedAt = now;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public BigDecimal getTransferPerOperation() {
        return transferPerOperation;
    }

    public BigDecimal getTransferDaily() {
        return transferDaily;
    }

    public BigDecimal getTransferMonthly() {
        return transferMonthly;
    }

    public BigDecimal getTransferNightPerOperation() {
        return transferNightPerOperation;
    }

    public BigDecimal getTransferNightWindow() {
        return transferNightWindow;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
