package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.services.MonthlyForecastService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/forecasts")
public class MonthlyForecastController {
    private final MonthlyForecastService service;

    public MonthlyForecastController(MonthlyForecastService service) {
        this.service = service;
    }

    @GetMapping("/{month}")
    public MonthlyForecastService.ForecastSummary get(@RequestAttribute UUID authenticatedUserId, @PathVariable String month) {
        return service.get(authenticatedUserId, parseMonth(month));
    }

    @PutMapping("/{month}")
    public MonthlyForecastService.ForecastSummary updateIncome(@RequestAttribute UUID authenticatedUserId,
        @PathVariable String month, @RequestBody IncomeInput input) {
        return service.updateIncome(authenticatedUserId, parseMonth(month), input == null ? null : input.expectedIncome());
    }

    @PostMapping("/{month}/expenses")
    public ResponseEntity<MonthlyForecastService.ForecastSummary> addExpense(@RequestAttribute UUID authenticatedUserId,
        @PathVariable String month, @RequestBody MonthlyForecastService.ExpenseInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addExpense(authenticatedUserId, parseMonth(month), input));
    }

    @PutMapping("/{month}/expenses/{expenseId}")
    public MonthlyForecastService.ForecastSummary updateExpense(@RequestAttribute UUID authenticatedUserId,
        @PathVariable String month, @PathVariable UUID expenseId, @RequestBody MonthlyForecastService.ExpenseInput input) {
        return service.updateExpense(authenticatedUserId, parseMonth(month), expenseId, input);
    }

    @DeleteMapping("/{month}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@RequestAttribute UUID authenticatedUserId,
        @PathVariable String month, @PathVariable UUID expenseId) {
        service.deleteExpense(authenticatedUserId, parseMonth(month), expenseId);
        return ResponseEntity.noContent().build();
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Mês inválido. Use o formato AAAA-MM");
        }
    }

    public record IncomeInput(BigDecimal expectedIncome) {}
}
