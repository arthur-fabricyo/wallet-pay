package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.wallet.config.LimitsProperties;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.domain.WalletLimitEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletLimitRepository;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateWalletLimitsUseCase {

    private final WalletRepository walletRepository;
    private final WalletLimitRepository walletLimitRepository;
    private final OperationLimitService operationLimitService;
    private final LimitsProperties properties;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public UpdateWalletLimitsUseCase(
            WalletRepository walletRepository,
            WalletLimitRepository walletLimitRepository,
            OperationLimitService operationLimitService,
            LimitsProperties properties,
            AuditLogger auditLogger,
            Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.walletLimitRepository = walletLimitRepository;
        this.operationLimitService = operationLimitService;
        this.properties = properties;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public WalletLimitsResult update(UpdateWalletLimitsCommand command) {
        WalletEntity wallet = walletRepository.findByUserId(command.userId())
                .orElseThrow(() -> new WalletNotFoundException("Carteira nao encontrada para o usuario autenticado"));

        BigDecimal perOperation = parseOptional(command.transferPerOperation());
        BigDecimal daily = parseOptional(command.transferDaily());
        BigDecimal monthly = parseOptional(command.transferMonthly());
        BigDecimal nightPerOperation = parseOptional(command.transferNightPerOperation());
        BigDecimal nightWindow = parseOptional(command.transferNightWindow());

        LimitsProperties.Transfer global = properties.transfer();
        Map<String, String> errors = new LinkedHashMap<>();
        requireWithinGlobal(errors, "transferPerOperation", perOperation, global.perOperation());
        requireWithinGlobal(errors, "transferDaily", daily, global.daily());
        requireWithinGlobal(errors, "transferMonthly", monthly, global.monthly());
        requireWithinGlobal(errors, "transferNightPerOperation", nightPerOperation, global.nightPerOperation());
        requireWithinGlobal(errors, "transferNightWindow", nightWindow, global.nightWindow());
        if (errors.isEmpty()) {
            errors.putAll(EffectiveTransferLimits
                    .of(global, perOperation, daily, monthly, nightPerOperation, nightWindow)
                    .coherenceViolations());
        }
        if (!errors.isEmpty()) {
            throw new InvalidLimitsException("Limites invalidos", errors);
        }

        WalletLimitEntity limits = walletLimitRepository.findById(wallet.getId())
                .orElseGet(() -> new WalletLimitEntity(wallet.getId()));
        Map<String, String> metadata = new LinkedHashMap<>(snapshot("previous", limits));
        limits.update(perOperation, daily, monthly, nightPerOperation, nightWindow, clock.instant());
        walletLimitRepository.saveAndFlush(limits);
        metadata.putAll(snapshot("new", limits));

        auditLogger.record(AuditEntry.success(AuditAction.LIMITS_UPDATED, wallet.getId(), metadata)
                .withActor(command.userId()));
        return operationLimitService.describe(wallet.getId(), clock.instant());
    }

    private static BigDecimal parseOptional(String raw) {
        return raw == null ? null : Money.parse(raw);
    }

    private static void requireWithinGlobal(
            Map<String, String> errors,
            String field,
            BigDecimal value,
            BigDecimal global
    ) {
        if (value != null && value.compareTo(global) > 0) {
            errors.put(field, "Limite acima do teto global de " + Money.format(global));
        }
    }

    private static Map<String, String> snapshot(String prefix, WalletLimitEntity limits) {
        return AuditEntry.metadata(
                prefix + ".transferPerOperation", format(limits.getTransferPerOperation()),
                prefix + ".transferDaily", format(limits.getTransferDaily()),
                prefix + ".transferMonthly", format(limits.getTransferMonthly()),
                prefix + ".transferNightPerOperation", format(limits.getTransferNightPerOperation()),
                prefix + ".transferNightWindow", format(limits.getTransferNightWindow())
        );
    }

    private static String format(BigDecimal value) {
        return value == null ? null : Money.format(value);
    }
}
