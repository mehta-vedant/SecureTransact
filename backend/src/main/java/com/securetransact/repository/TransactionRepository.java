package com.securetransact.repository;

import com.securetransact.model.Transaction;
import com.securetransact.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t LEFT JOIN t.fromAccount fa LEFT JOIN t.toAccount ta WHERE fa.id = :accountId OR ta.id = :accountId ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t LEFT JOIN t.fromAccount fa LEFT JOIN fa.user fu LEFT JOIN t.toAccount ta LEFT JOIN ta.user tu WHERE fu.id = :userId OR tu.id = :userId ORDER BY t.createdAt DESC")
    Page<Transaction> findByUserId(@Param("userId") Long userId, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    // For fraud detection: count recent transactions from an account
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.fromAccount.id = :accountId AND t.createdAt > :since")
    long countRecentTransactions(@Param("accountId") Long accountId, @Param("since") LocalDateTime since);

    // For fraud detection: count recent transfers to a specific account
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.toAccount.id = :toAccountId AND t.fromAccount.id = :fromAccountId AND t.createdAt > :since")
    long countRecentTransfersToAccount(@Param("fromAccountId") Long fromAccountId, @Param("toAccountId") Long toAccountId, @Param("since") LocalDateTime since);

    // For fraud detection ML features: distinct recipients from an account within a window
    @Query("SELECT COUNT(DISTINCT t.toAccount.id) FROM Transaction t WHERE t.fromAccount.id = :fromAccountId AND t.toAccount.id IS NOT NULL AND t.createdAt > :since")
    long countDistinctRecipientsSince(@Param("fromAccountId") Long fromAccountId, @Param("since") LocalDateTime since);

    // For fraud detection ML features: average amount sent from an account since a point in time
    @Query("SELECT COALESCE(AVG(t.amount), 0) FROM Transaction t WHERE t.fromAccount.id = :fromAccountId AND t.createdAt > :since")
    BigDecimal avgAmountSince(@Param("fromAccountId") Long fromAccountId, @Param("since") LocalDateTime since);

    // For fraud detection ML features: has this recipient ever received a transfer from this account
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.fromAccount.id = :fromAccountId AND t.toAccount.id = :toAccountId")
    long countTransfersToAccount(@Param("fromAccountId") Long fromAccountId, @Param("toAccountId") Long toAccountId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.fromAccount.id = :accountId AND t.createdAt > :since AND t.id <> :excludedTransactionId")
    long countRecentTransactionsBeforeDecision(@Param("accountId") Long accountId, @Param("since") LocalDateTime since,
                                               @Param("excludedTransactionId") Long excludedTransactionId);

    @Query("SELECT COUNT(DISTINCT t.toAccount.id) FROM Transaction t WHERE t.fromAccount.id = :fromAccountId AND t.toAccount.id IS NOT NULL AND t.createdAt > :since AND t.id <> :excludedTransactionId")
    long countDistinctRecipientsBeforeDecision(@Param("fromAccountId") Long fromAccountId, @Param("since") LocalDateTime since,
                                               @Param("excludedTransactionId") Long excludedTransactionId);

    @Query("SELECT COALESCE(AVG(t.amount), 0) FROM Transaction t WHERE t.fromAccount.id = :fromAccountId AND t.createdAt > :since AND t.id <> :excludedTransactionId")
    BigDecimal avgAmountBeforeDecision(@Param("fromAccountId") Long fromAccountId, @Param("since") LocalDateTime since,
                                       @Param("excludedTransactionId") Long excludedTransactionId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.fromAccount.id = :fromAccountId AND t.toAccount.id = :toAccountId AND t.id <> :excludedTransactionId")
    long countTransfersToAccountBeforeDecision(@Param("fromAccountId") Long fromAccountId, @Param("toAccountId") Long toAccountId,
                                               @Param("excludedTransactionId") Long excludedTransactionId);

    // Dashboard metrics
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.createdAt > :since")
    long countTransactionsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.createdAt > :since AND t.status = 'COMPLETED'")
    BigDecimal sumCompletedAmountSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = :status AND t.createdAt > :since")
    long countByStatusSince(@Param("status") TransactionStatus status, @Param("since") LocalDateTime since);

    // Statement: transactions for an account within date range
    @Query("SELECT t FROM Transaction t LEFT JOIN t.fromAccount fa LEFT JOIN t.toAccount ta WHERE (fa.id = :accountId OR ta.id = :accountId) AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountIdAndDateRange(@Param("accountId") Long accountId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
