package ElFabrica.Wallet_pay.transaction.repository;

import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import ElFabrica.Wallet_pay.transaction.domain.TransactionStatus;
import ElFabrica.Wallet_pay.transaction.domain.TransactionType;
import java.math.BigDecimal;
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
              and (cast(:from as Instant) is null or t.createdAt >= :from)
              and (cast(:to as Instant) is null or t.createdAt <= :to)
              and (:type is null or t.type = :type)
            """)
    Page<TransactionEntity> findStatementTransactions(
            @Param("walletId") UUID walletId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("type") TransactionType type,
            Pageable pageable
    );

    @Query("""
            select sum(t.amount)
            from TransactionEntity t
            where t.senderWallet.id = :walletId
              and t.type = :type
              and t.status = :status
              and t.createdAt >= :from
              and t.createdAt <= :to
            """)
    BigDecimal sumOutgoingAmount(
            @Param("walletId") UUID walletId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            select sum(t.amount)
            from TransactionEntity t
            where t.receiverWallet.id = :walletId
              and t.type = :type
              and t.status = :status
              and t.createdAt >= :from
              and t.createdAt <= :to
            """)
    BigDecimal sumIncomingAmount(
            @Param("walletId") UUID walletId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
