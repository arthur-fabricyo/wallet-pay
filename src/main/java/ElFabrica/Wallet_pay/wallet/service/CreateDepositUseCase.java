package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.idempotency.IdempotencyRequest;
import ElFabrica.Wallet_pay.common.idempotency.IdempotencyService;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentOperation;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.common.idempotency.RequestHash;
import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.transaction.domain.TransactionDescription;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateDepositUseCase {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final IdempotencyService idempotencyService;
    private final AuditLogger auditLogger;
    private final OperationLimitService operationLimitService;

    public CreateDepositUseCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            Clock clock,
            IdempotencyService idempotencyService,
            AuditLogger auditLogger,
            OperationLimitService operationLimitService
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.idempotencyService = idempotencyService;
        this.auditLogger = auditLogger;
        this.operationLimitService = operationLimitService;
    }

    @Transactional
    public IdempotentResult<DepositResult> deposit(DepositCommand command) {
        BigDecimal amount = Money.parse(command.amount());
        String description = TransactionDescription.normalize(command.description());
        IdempotencyRequest idempotencyRequest = new IdempotencyRequest(
                command.userId(),
                IdempotentOperation.DEPOSIT_CREATE,
                command.idempotencyKey(),
                RequestHash.of(Money.format(amount), description)
        );

        return idempotencyService.execute(
                idempotencyRequest,
                DepositResult.class,
                () -> executeDeposit(command, amount, description)
        );
    }

    private DepositResult executeDeposit(DepositCommand command, BigDecimal amount, String description) {
        Instant now = clock.instant();
        WalletEntity wallet = walletRepository.findByUserId(command.userId())
                .orElseThrow(() -> new WalletNotFoundException("Carteira nao encontrada para o usuario autenticado"));

        operationLimitService.checkDeposit(wallet.getId(), amount, now);
        wallet.credit(amount);
        TransactionEntity transaction = transactionRepository.saveAndFlush(
                TransactionEntity.completedDeposit(wallet, amount, description, now)
        );

        auditLogger.record(AuditEntry.success(
                AuditAction.DEPOSIT_CREATED,
                transaction.getId(),
                AuditEntry.metadata(
                        "amount", Money.format(amount),
                        "currency", transaction.getCurrency(),
                        "walletId", wallet.getId()
                )
        ).withActor(command.userId()));

        return new DepositResult(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                Money.format(transaction.getAmount()),
                transaction.getCurrency(),
                transaction.getDescription(),
                new DepositWalletResult(
                        wallet.getId(),
                        Money.format(wallet.getBalance()),
                        wallet.getCurrency(),
                        wallet.getUpdatedAt()
                ),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
}
