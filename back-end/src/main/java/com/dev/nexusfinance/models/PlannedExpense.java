package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "tb_planned_expenses",
    indexes = @Index(name = "idx_planned_expense_forecast_due", columnList = "id_forecast,due_date")
)
@Getter @Setter
public class PlannedExpense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_planned_expense", updatable = false, nullable = false)
    private UUID idPlannedExpense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_forecast", nullable = false)
    private MonthlyForecast forecast;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Version
    @Column(nullable = false)
    private long version;
}
