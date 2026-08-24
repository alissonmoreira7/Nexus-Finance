package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.models.MonthlyForecast;
import com.dev.nexusfinance.models.PlannedExpense;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.MonthlyForecastRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MonthlyForecastServiceTest {
    private MonthlyForecastRepository forecasts;
    private EntityManager entityManager;
    private MonthlyForecastService service;
    private User user;
    private final YearMonth month = YearMonth.of(2026, 8);

    @BeforeEach
    void setUp() {
        forecasts = mock(MonthlyForecastRepository.class);
        entityManager = mock(EntityManager.class);
        service = new MonthlyForecastService(forecasts, entityManager);
        user = new User();
        user.setIdUser(UUID.randomUUID());
        when(entityManager.find(User.class, user.getIdUser(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);
        when(forecasts.saveAndFlush(any(MonthlyForecast.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void retornaMesVazioSemCriarRegistro() {
        when(forecasts.findByUserAndMonth(user.getIdUser(), month.atDay(1))).thenReturn(Optional.empty());

        MonthlyForecastService.ForecastSummary result = service.get(user.getIdUser(), month);

        assertEquals(new BigDecimal("0.00"), result.expectedIncome());
        assertEquals(new BigDecimal("0.00"), result.totalPlanned());
        assertEquals(new BigDecimal("0.00"), result.projectedBalance());
        verify(forecasts, never()).save(any());
    }

    @Test
    void calculaTotalESaldoProjetadoNoServidor() {
        MonthlyForecast forecast = forecast(new BigDecimal("5000.00"));
        forecast.addExpense(expense("Aluguel", "1500.00", LocalDate.of(2026, 8, 5)));
        forecast.addExpense(expense("Cartão", "850.50", LocalDate.of(2026, 8, 10)));
        when(forecasts.findByUserAndMonth(user.getIdUser(), month.atDay(1))).thenReturn(Optional.of(forecast));

        MonthlyForecastService.ForecastSummary result = service.get(user.getIdUser(), month);

        assertEquals(new BigDecimal("2350.50"), result.totalPlanned());
        assertEquals(new BigDecimal("2649.50"), result.projectedBalance());
    }

    @Test
    void criaRendaMensalComBloqueioEPrecisaoMonetaria() {
        when(forecasts.findByUserAndMonth(user.getIdUser(), month.atDay(1))).thenReturn(Optional.empty());

        MonthlyForecastService.ForecastSummary result = service.updateIncome(user.getIdUser(), month, new BigDecimal("4200.5"));

        assertEquals(new BigDecimal("4200.50"), result.expectedIncome());
        verify(entityManager).find(User.class, user.getIdUser(), LockModeType.PESSIMISTIC_WRITE);
        verify(forecasts).saveAndFlush(any(MonthlyForecast.class));
    }

    @Test
    void rejeitaValoresInvalidosEDataDeOutroMes() {
        assertThrows(IllegalArgumentException.class,
            () -> service.updateIncome(user.getIdUser(), month, new BigDecimal("10.001")));
        assertThrows(IllegalArgumentException.class,
            () -> service.addExpense(user.getIdUser(), month,
                new MonthlyForecastService.ExpenseInput("Aluguel", BigDecimal.ZERO, null)));
        assertThrows(IllegalArgumentException.class,
            () -> service.addExpense(user.getIdUser(), month,
                new MonthlyForecastService.ExpenseInput("Aluguel", BigDecimal.TEN, LocalDate.of(2026, 9, 1))));
    }

    @Test
    void naoAtualizaGastoQueNaoPertenceAoUsuarioEMes() {
        MonthlyForecast forecast = forecast(new BigDecimal("3000.00"));
        PlannedExpense owned = expense("Aluguel", "1000.00", null);
        owned.setIdPlannedExpense(UUID.randomUUID());
        forecast.addExpense(owned);
        when(forecasts.findByUserAndMonth(user.getIdUser(), month.atDay(1))).thenReturn(Optional.of(forecast));

        assertThrows(ResourceNotFoundException.class, () -> service.updateExpense(
            user.getIdUser(), month, UUID.randomUUID(),
            new MonthlyForecastService.ExpenseInput("Cartão", new BigDecimal("500.00"), null)
        ));
    }

    @Test
    void naoEncontraPlanejamentoDeOutroUsuarioPeloIdDoGasto() {
        User otherUser = new User();
        otherUser.setIdUser(UUID.randomUUID());
        when(entityManager.find(User.class, otherUser.getIdUser(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(otherUser);
        when(forecasts.findByUserAndMonth(otherUser.getIdUser(), month.atDay(1))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateExpense(
            otherUser.getIdUser(), month, UUID.randomUUID(),
            new MonthlyForecastService.ExpenseInput("Cartão", new BigDecimal("500.00"), null)
        ));

        verify(forecasts).findByUserAndMonth(otherUser.getIdUser(), month.atDay(1));
    }

    private MonthlyForecast forecast(BigDecimal income) {
        MonthlyForecast forecast = new MonthlyForecast();
        forecast.setUser(user);
        forecast.setReferenceMonth(month.atDay(1));
        forecast.setExpectedIncome(income);
        return forecast;
    }

    private PlannedExpense expense(String description, String amount, LocalDate dueDate) {
        PlannedExpense expense = new PlannedExpense();
        expense.setIdPlannedExpense(UUID.randomUUID());
        expense.setDescription(description);
        expense.setAmount(new BigDecimal(amount));
        expense.setDueDate(dueDate);
        return expense;
    }
}
