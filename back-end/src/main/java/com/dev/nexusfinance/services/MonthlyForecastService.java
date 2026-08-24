package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.models.MonthlyForecast;
import com.dev.nexusfinance.models.PlannedExpense;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.MonthlyForecastRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MonthlyForecastService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999999999999.99");

    private final MonthlyForecastRepository forecasts;
    private final EntityManager entityManager;

    public MonthlyForecastService(MonthlyForecastRepository forecasts, EntityManager entityManager) {
        this.forecasts = forecasts;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public ForecastSummary get(UUID userId, YearMonth month) {
        LocalDate referenceMonth = reference(month);
        return forecasts.findByUserAndMonth(userId, referenceMonth)
            .map(this::summary)
            .orElseGet(() -> emptySummary(month));
    }

    @Transactional
    public ForecastSummary updateIncome(UUID userId, YearMonth month, BigDecimal expectedIncome) {
        BigDecimal normalizedIncome = money(expectedIncome, true, "Renda prevista");
        User user = lockUser(userId);
        MonthlyForecast forecast = getOrCreate(user, reference(month));
        forecast.setExpectedIncome(normalizedIncome);
        return summary(forecasts.saveAndFlush(forecast));
    }

    @Transactional
    public ForecastSummary addExpense(UUID userId, YearMonth month, ExpenseInput input) {
        ValidExpense valid = validateExpense(month, input);
        User user = lockUser(userId);
        MonthlyForecast forecast = getOrCreate(user, reference(month));
        PlannedExpense expense = new PlannedExpense();
        apply(expense, valid);
        forecast.addExpense(expense);
        return summary(forecasts.saveAndFlush(forecast));
    }

    @Transactional
    public ForecastSummary updateExpense(UUID userId, YearMonth month, UUID expenseId, ExpenseInput input) {
        ValidExpense valid = validateExpense(month, input);
        lockUser(userId);
        MonthlyForecast forecast = ownedForecast(userId, month);
        PlannedExpense expense = ownedExpense(forecast, expenseId);
        apply(expense, valid);
        return summary(forecasts.saveAndFlush(forecast));
    }

    @Transactional
    public void deleteExpense(UUID userId, YearMonth month, UUID expenseId) {
        lockUser(userId);
        MonthlyForecast forecast = ownedForecast(userId, month);
        forecast.removeExpense(ownedExpense(forecast, expenseId));
        forecasts.saveAndFlush(forecast);
    }

    private User lockUser(UUID userId) {
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) throw new ResourceNotFoundException("Usuário não encontrado");
        return user;
    }

    private MonthlyForecast getOrCreate(User user, LocalDate month) {
        return forecasts.findByUserAndMonth(user.getIdUser(), month).orElseGet(() -> {
            MonthlyForecast forecast = new MonthlyForecast();
            forecast.setUser(user);
            forecast.setReferenceMonth(month);
            forecast.setExpectedIncome(BigDecimal.ZERO.setScale(2));
            return forecast;
        });
    }

    private MonthlyForecast ownedForecast(UUID userId, YearMonth month) {
        return forecasts.findByUserAndMonth(userId, reference(month))
            .orElseThrow(() -> new ResourceNotFoundException("Planejamento mensal não encontrado"));
    }

    private PlannedExpense ownedExpense(MonthlyForecast forecast, UUID expenseId) {
        return forecast.getExpenses().stream()
            .filter(expense -> expense.getIdPlannedExpense().equals(expenseId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Gasto previsto não encontrado"));
    }

    private ValidExpense validateExpense(YearMonth month, ExpenseInput input) {
        if (input == null || input.description() == null || input.description().isBlank()) {
            throw new IllegalArgumentException("Descrição do gasto é obrigatória");
        }
        String description = input.description().trim();
        if (description.length() > 120) throw new IllegalArgumentException("Descrição deve ter no máximo 120 caracteres");
        BigDecimal amount = money(input.amount(), false, "Valor previsto");
        if (input.dueDate() != null && !YearMonth.from(input.dueDate()).equals(month)) {
            throw new IllegalArgumentException("Vencimento deve pertencer ao mês selecionado");
        }
        return new ValidExpense(description, amount, input.dueDate());
    }

    private BigDecimal money(BigDecimal value, boolean allowZero, String field) {
        if (value == null) throw new IllegalArgumentException(field + " é obrigatória");
        if (value.scale() > 2) throw new IllegalArgumentException(field + " deve ter no máximo duas casas decimais");
        if (value.signum() < 0 || (!allowZero && value.signum() == 0)) {
            throw new IllegalArgumentException(field + (allowZero ? " não pode ser negativa" : " deve ser maior que zero"));
        }
        if (value.compareTo(MAX_MONEY) > 0) throw new IllegalArgumentException(field + " excede o limite permitido");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void apply(PlannedExpense expense, ValidExpense valid) {
        expense.setDescription(valid.description());
        expense.setAmount(valid.amount());
        expense.setDueDate(valid.dueDate());
    }

    private ForecastSummary summary(MonthlyForecast forecast) {
        List<ExpenseView> expenses = forecast.getExpenses().stream()
            .sorted(Comparator.comparing(PlannedExpense::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PlannedExpense::getDescription, String.CASE_INSENSITIVE_ORDER))
            .map(expense -> new ExpenseView(expense.getIdPlannedExpense(), expense.getDescription(), expense.getAmount(), expense.getDueDate()))
            .toList();
        BigDecimal total = expenses.stream().map(ExpenseView::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal income = forecast.getExpectedIncome().setScale(2, RoundingMode.UNNECESSARY);
        return new ForecastSummary(YearMonth.from(forecast.getReferenceMonth()).toString(), income, total, income.subtract(total), expenses);
    }

    private ForecastSummary emptySummary(YearMonth month) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new ForecastSummary(month.toString(), zero, zero, zero, List.of());
    }

    private LocalDate reference(YearMonth month) {
        if (month == null) throw new IllegalArgumentException("Mês é obrigatório");
        return month.atDay(1);
    }

    public record ExpenseInput(String description, BigDecimal amount, LocalDate dueDate) {}
    public record ExpenseView(UUID id, String description, BigDecimal amount, LocalDate dueDate) {}
    public record ForecastSummary(String month, BigDecimal expectedIncome, BigDecimal totalPlanned,
                                  BigDecimal projectedBalance, List<ExpenseView> expenses) {}
    private record ValidExpense(String description, BigDecimal amount, LocalDate dueDate) {}
}
