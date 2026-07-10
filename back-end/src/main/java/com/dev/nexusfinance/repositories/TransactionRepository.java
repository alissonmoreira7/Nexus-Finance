package com.dev.nexusfinance.repositories;

import com.dev.nexusfinance.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByAccount_IdAccount(UUID accountId);
    List<Transaction> findByAccountIdAndType(UUID accountId, String type);
}
