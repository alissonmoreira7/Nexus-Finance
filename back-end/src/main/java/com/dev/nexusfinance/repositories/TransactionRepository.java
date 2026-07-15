package com.dev.nexusfinance.repositories;

import com.dev.nexusfinance.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import com.dev.nexusfinance.models.TransactionSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    @EntityGraph(attributePaths = "category")
    @Query("select t from Transaction t where t.account.idAccount = :accountId order by t.transaction_date desc, t.idTransaction desc")
    Page<Transaction> findByAccount_IdAccount(@Param("accountId") UUID accountId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("select t from Transaction t where t.account.idAccount = :accountId and (t.source = :source or (t.source is null and :source = com.dev.nexusfinance.models.TransactionSource.CSV)) order by t.transaction_date desc, t.idTransaction desc")
    Page<Transaction> findByAccountAndSource(@Param("accountId") UUID accountId,
                                             @Param("source") TransactionSource source,
                                             Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.account.idAccount = :accountId and t.transaction_date >= :start and t.transaction_date < :end and t.category.type = :type")
    BigDecimal sumByType(@Param("accountId") UUID accountId, @Param("start") LocalDate start,
                         @Param("end") LocalDate end,
                         @Param("type") com.dev.nexusfinance.models.CategoryType type);

    @Query("select t.category.name, sum(t.amount) from Transaction t where t.account.idAccount = :accountId and t.transaction_date >= :start and t.transaction_date < :end and t.category.type = com.dev.nexusfinance.models.CategoryType.EXPENSE group by t.category.name order by sum(t.amount) desc")
    java.util.List<Object[]> sumExpensesByCategory(@Param("accountId") UUID accountId,
                                                    @Param("start") LocalDate start,
                                                    @Param("end") LocalDate end);

}
