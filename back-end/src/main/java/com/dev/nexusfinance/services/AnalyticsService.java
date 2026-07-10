package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    @Autowired
    private TransactionRepository transactionRepository;

    public record AnalyticsSummary(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        Map<String, BigDecimal> expensesByCategory
    ) {}

    public AnalyticsSummary getSummary(UUID accountId) {
        List<Transaction> transactions = transactionRepository
        .findByAccount_IdAccount(accountId);

        LocalDate now = LocalDate.now();
        List<Transaction> doMes = transactions.stream()
            .filter(tx -> tx.getTransaction_date().getMonth() == now.getMonth()
                    && tx.getTransaction_date().getYear() == now.getYear())
            .collect(Collectors.toList());

        // soma receitas
        BigDecimal totalIncome = doMes.stream()
            .filter(tx -> tx.getCategory().getType() == CategoryType.INCOME)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // soma despesas
        BigDecimal totalExpense = doMes.stream()
            .filter(tx -> tx.getCategory().getType() == CategoryType.EXPENSE)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // agrupa gastos por categoria
        Map<String, BigDecimal> expensesByCategory = new HashMap<>();
        doMes.stream()
            .filter(tx -> tx.getCategory().getType() == CategoryType.EXPENSE)
            .forEach(tx -> {
                String catName = tx.getCategory().getName();
                expensesByCategory.merge(catName, tx.getAmount(), BigDecimal::add);
            });

        BigDecimal balance = totalIncome.subtract(totalExpense);

        return new AnalyticsSummary(totalIncome, totalExpense, balance, expensesByCategory);
    }
}
