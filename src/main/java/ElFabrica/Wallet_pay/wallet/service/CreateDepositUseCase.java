package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.transaction.domain.TransactionDescription;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateDepositUseCase {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public CreateDepositUseCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public DepositResult deposit(DepositCommand command) {
        BigDecimal amount = Money.parse(command.amount());
        String description = TransactionDescription.normalize(command.description());
        WalletEntity wallet = walletRepository.findByUserId(command.userId())
                .orElseThrow(() -> new WalletNotFoundException("Carteira nao encontrada para o usuario autenticado"));

        wallet.credit(amount);
        TransactionEntity transaction = transactionRepository.saveAndFlush(
                TransactionEntity.completedDeposit(wallet, amount, description, clock.instant())
        );

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
