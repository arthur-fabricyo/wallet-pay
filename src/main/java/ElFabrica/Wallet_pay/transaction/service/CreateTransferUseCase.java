package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.transaction.domain.TransactionDescription;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import ElFabrica.Wallet_pay.wallet.service.WalletNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTransferUseCase {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public CreateTransferUseCase(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public TransferResult transfer(TransferCommand command) {
        if (command.senderUserId().equals(command.receiverUserId())) {
            throw new InvalidTransferException("Usuario nao pode transferir para si mesmo");
        }

        BigDecimal amount = Money.parse(command.amount());
        WalletEntity senderWallet = walletRepository.findByUserId(command.senderUserId())
                .orElseThrow(() -> new WalletNotFoundException("Carteira de origem nao encontrada"));
        WalletEntity receiverWallet = walletRepository.findByUserId(command.receiverUserId())
                .orElseThrow(() -> new WalletNotFoundException("Carteira de destino nao encontrada"));

        if (!WalletEntity.DEFAULT_CURRENCY.equals(senderWallet.getCurrency())
                || !WalletEntity.DEFAULT_CURRENCY.equals(receiverWallet.getCurrency())) {
            throw new InvalidTransferException("Moeda da transferencia deve ser BRL");
        }

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Saldo insuficiente");
        }

        senderWallet.debit(amount);
        receiverWallet.credit(amount);

        TransactionEntity transaction = transactionRepository.saveAndFlush(TransactionEntity.completedTransfer(
                senderWallet,
                receiverWallet,
                amount,
                TransactionDescription.normalize(command.description()),
                clock.instant()
        ));

        return new TransferResult(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                Money.format(transaction.getAmount()),
                transaction.getCurrency(),
                senderWallet.getId(),
                receiverWallet.getId(),
                new TransferSenderWalletResult(
                        senderWallet.getId(),
                        Money.format(senderWallet.getBalance()),
                        senderWallet.getCurrency(),
                        senderWallet.getUpdatedAt()
                ),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
}
