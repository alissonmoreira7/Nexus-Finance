package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "tb_monthly_forecasts",
    uniqueConstraints = @UniqueConstraint(name = "uk_forecast_user_month", columnNames = {"id_user", "reference_month"})
)
@Getter @Setter
public class MonthlyForecast {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_forecast", updatable = false, nullable = false)
    private UUID idForecast;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "expected_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedIncome = BigDecimal.ZERO.setScale(2);

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "forecast", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlannedExpense> expenses = new ArrayList<>();

    public void addExpense(PlannedExpense expense) {
        expenses.add(expense);
        expense.setForecast(this);
    }

    public void removeExpense(PlannedExpense expense) {
        expenses.remove(expense);
        expense.setForecast(null);
    }
}
