package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import ElFabrica.Wallet_pay.transaction.repository.TransactionRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetWalletStatementUseCase {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "createdAt,desc";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public GetWalletStatementUseCase(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public StatementResult getStatement(UUID authenticatedUserId, StatementQuery query) {
        WalletEntity wallet = walletRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new WalletNotFoundException("Carteira nao encontrada para o usuario autenticado"));

        ValidStatementQuery validQuery = validate(query);
        Page<TransactionEntity> transactions = transactionRepository.findStatementTransactions(
                wallet.getId(),
                validQuery.from(),
                validQuery.to(),
                validQuery.type(),
                PageRequest.of(validQuery.page(), validQuery.size(), validQuery.sort())
        );

        return new StatementResult(
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages(),
                transactions.map(transaction -> entry(wallet.getId(), transaction)).toList()
        );
    }

    private StatementEntryResult entry(UUID walletId, TransactionEntity transaction) {
        WalletEntity senderWallet = transaction.getSenderWallet();
        boolean outgoing = senderWallet != null && senderWallet.getId().equals(walletId);
        WalletEntity counterparty = outgoing ? transaction.getReceiverWallet() : senderWallet;

        return new StatementEntryResult(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                outgoing ? StatementDirection.OUT : StatementDirection.IN,
                Money.format(transaction.getAmount()),
                transaction.getCurrency(),
                counterparty == null ? null : counterparty.getUser().getName(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

    private ValidStatementQuery validate(StatementQuery query) {
        int page = query == null ? DEFAULT_PAGE : query.page();
        int size = query == null ? DEFAULT_SIZE : query.size();
        String sortValue = blankToDefault(query == null ? null : query.sort(), DEFAULT_SORT);

        if (page < 0) {
            throw new InvalidStatementQueryException("page deve ser maior ou igual a zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidStatementQueryException("size deve estar entre 1 e 100");
        }

        Instant from = parseInstant(query == null ? null : query.from(), "from");
        Instant to = parseInstant(query == null ? null : query.to(), "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidStatementQueryException("from nao pode ser maior que to");
        }

        TransactionType type = parseType(query == null ? null : query.type());
        Sort sort = parseSort(sortValue);

        return new ValidStatementQuery(page, size, from, to, type, sort);
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidStatementQueryException(field + " deve estar em formato ISO-8601");
        }
    }

    private TransactionType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidStatementQueryException("type invalido");
        }
    }

    private Sort parseSort(String value) {
        return switch (value) {
            case "createdAt,asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "createdAt,desc" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> throw new InvalidStatementQueryException("sort invalido");
        };
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record ValidStatementQuery(
            int page,
            int size,
            Instant from,
            Instant to,
            TransactionType type,
            Sort sort
    ) {
    }
}
