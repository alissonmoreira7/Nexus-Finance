package com.dev.nexusfinance.services;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class AnalyticsService {
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    public AnalyticsService(TransactionRepository transactions, AccountRepository accounts) { this.transactions = transactions; this.accounts = accounts; }
    @Transactional(readOnly = true)
    public AnalyticsSummary getSummary(UUID accountId) {
        if (!accounts.existsById(accountId)) throw new ResourceNotFoundException("Conta não encontrada");
        LocalDate start = LocalDate.now().withDayOfMonth(1), end = start.plusMonths(1);
        BigDecimal income = value(transactions.sumByType(accountId, start, end, CategoryType.INCOME));
        BigDecimal expense = value(transactions.sumByType(accountId, start, end, CategoryType.EXPENSE));
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        transactions.sumExpensesByCategory(accountId, start, end).forEach(row -> byCategory.put((String) row[0], value((BigDecimal) row[1])));
        return new AnalyticsSummary(income, expense, income.subtract(expense), byCategory);
    }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    public record AnalyticsSummary(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance, Map<String, BigDecimal> expensesByCategory) {}
}
