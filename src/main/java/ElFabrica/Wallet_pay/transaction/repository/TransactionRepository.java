package ElFabrica.Wallet_pay.transaction.repository;

import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    @EntityGraph(attributePaths = {
            "senderWallet",
            "senderWallet.user",
            "receiverWallet",
            "receiverWallet.user"
    })
    Optional<TransactionEntity> findReceiptById(UUID id);

    @EntityGraph(attributePaths = {
            "senderWallet",
            "senderWallet.user",
            "receiverWallet",
            "receiverWallet.user"
    })
    @Query("""
            select t
            from TransactionEntity t
            where (t.senderWallet.id = :walletId or t.receiverWallet.id = :walletId)
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt <= :to)
              and (:type is null or t.type = :type)
            """)
    Page<TransactionEntity> findStatementTransactions(
            @Param("walletId") UUID walletId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("type") TransactionType type,
            Pageable pageable
    );
}
