package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.wallet.config.LimitsProperties;
import ElFabrica.Wallet_pay.wallet.domain.WalletLimitEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletLimitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class OperationLimitService {

    private final LimitsProperties properties;
    private final WalletLimitRepository walletLimitRepository;
    private final TransactionRepository transactionRepository;

    public OperationLimitService(
            LimitsProperties properties,
            WalletLimitRepository walletLimitRepository,
            TransactionRepository transactionRepository
    ) {
        this.properties = properties;
        this.walletLimitRepository = walletLimitRepository;
        this.transactionRepository = transactionRepository;
    }

    public EffectiveTransferLimits transferLimits(UUID walletId) {
        return effectiveTransferLimits(walletLimitRepository.findById(walletId).orElse(null));
    }

    public void checkTransfer(UUID walletId, BigDecimal amount, Instant now) {
        EffectiveTransferLimits limits = transferLimits(walletId);
        Optional<LimitPeriods.Window> night = nightWindow(now);

        requireWithinOperation(amount, limits.perOperation(), LimitType.PER_OPERATION,
                "Limite por operacao de transferencia excedido");
        if (night.isPresent()) {
            requireWithinOperation(amount, limits.nightPerOperation(), LimitType.NIGHT_PER_OPERATION,
                    "Limite noturno por operacao de transferencia excedido");
        }
        requireWithinPeriod(amount, limits.daily(),
                sentTransfers(walletId, LimitPeriods.day(now, zone()).start(), now),
                LimitType.DAILY, "Limite diario de transferencia excedido");
        requireWithinPeriod(amount, limits.monthly(),
                sentTransfers(walletId, LimitPeriods.month(now, zone()).start(), now),
                LimitType.MONTHLY, "Limite mensal de transferencia excedido");
        if (night.isPresent()) {
            requireWithinPeriod(amount, limits.nightWindow(),
                    sentTransfers(walletId, night.get().start(), now),
                    LimitType.NIGHT_WINDOW, "Limite noturno de transferencia excedido");
        }
    }

    public void checkDeposit(UUID walletId, BigDecimal amount, Instant now) {
        LimitsProperties.Deposit limits = properties.deposit();

        requireWithinOperation(amount, limits.perOperation(), LimitType.PER_OPERATION,
                "Limite por operacao de deposito excedido");
        requireWithinPeriod(amount, limits.daily(),
                receivedDeposits(walletId, LimitPeriods.day(now, zone()).start(), now),
                LimitType.DAILY, "Limite diario de deposito excedido");
    }

    public BigDecimal sentTransfers(UUID walletId, Instant from, Instant to) {
        return orZero(transactionRepository.sumOutgoingAmount(
                walletId, TransactionType.TRANSFER, TransactionStatus.COMPLETED, from, to
        ));
    }

    public BigDecimal receivedDeposits(UUID walletId, Instant from, Instant to) {
        return orZero(transactionRepository.sumIncomingAmount(
                walletId, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, from, to
        ));
    }

    public Optional<LimitPeriods.Window> nightWindow(Instant now) {
        return LimitPeriods.night(now, zone(), properties.night().startTime(), properties.night().endTime());
    }

    private EffectiveTransferLimits effectiveTransferLimits(WalletLimitEntity custom) {
        return EffectiveTransferLimits.of(
                properties.transfer(),
                customValue(custom, WalletLimitEntity::getTransferPerOperation),
                customValue(custom, WalletLimitEntity::getTransferDaily),
                customValue(custom, WalletLimitEntity::getTransferMonthly),
                customValue(custom, WalletLimitEntity::getTransferNightPerOperation),
                customValue(custom, WalletLimitEntity::getTransferNightWindow)
        );
    }

    private ZoneId zone() {
        return properties.zoneId();
    }

    private static BigDecimal customValue(WalletLimitEntity custom, Function<WalletLimitEntity, BigDecimal> getter) {
        return custom == null ? null : getter.apply(custom);
    }

    private static void requireWithinOperation(BigDecimal amount, BigDecimal limit, LimitType type, String message) {
        if (amount.compareTo(limit) > 0) {
            throw new LimitExceededException(type, message, limit, limit);
        }
    }

    private static void requireWithinPeriod(
            BigDecimal amount,
            BigDecimal limit,
            BigDecimal used,
            LimitType type,
            String message
    ) {
        if (used.add(amount).compareTo(limit) > 0) {
            throw new LimitExceededException(type, message, limit, limit.subtract(used).max(BigDecimal.ZERO));
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
