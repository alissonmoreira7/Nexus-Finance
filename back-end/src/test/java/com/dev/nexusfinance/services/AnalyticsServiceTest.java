package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link AnalyticsService}.
 *
 * <p>Esta classe estava com 0% de cobertura, e nao por descuido: o servico chamava
 * {@code LocalDate.now()} direto, o que tornava a janela de apuracao impossivel de
 * controlar em teste. Com o {@link Clock} injetado (QA-13), a data virou parametro.
 */
class AnalyticsServiceTest {
    private static final Instant DEZ_DE_AGOSTO = Instant.parse("2026-08-10T15:30:00Z");

    private TransactionRepository transactions;
    private AccountRepository accounts;
    private AnalyticsService service;
    private UUID contaId;

    @BeforeEach
    void setUp() {
        transactions = mock(TransactionRepository.class);
        accounts = mock(AccountRepository.class);
        service = serviceEm(DEZ_DE_AGOSTO);

        contaId = UUID.randomUUID();
        when(accounts.existsById(contaId)).thenReturn(true);
        when(transactions.sumExpensesByCategory(any(), any(), any())).thenReturn(List.<Object[]>of());
    }

    private AnalyticsService serviceEm(Instant instante) {
        return new AnalyticsService(transactions, accounts, Clock.fixed(instante, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("exige conta existente")
    void exigeContaExistente() {
        UUID inexistente = UUID.randomUUID();
        when(accounts.existsById(inexistente)).thenReturn(false);

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.getSummary(inexistente))
            .withMessage("Conta não encontrada");
    }

    @Test
    @DisplayName("apura entradas, saidas e saldo do mes corrente")
    void apuraEntradasSaidasESaldo() {
        when(transactions.sumByType(contaId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), CategoryType.INCOME))
            .thenReturn(new BigDecimal("5000.00"));
        when(transactions.sumByType(contaId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), CategoryType.EXPENSE))
            .thenReturn(new BigDecimal("1850.50"));

        AnalyticsService.AnalyticsSummary resumo = service.getSummary(contaId);

        assertThat(resumo.totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(resumo.totalExpense()).isEqualByComparingTo("1850.50");
        assertThat(resumo.balance()).isEqualByComparingTo("3149.50");
    }

    @Test
    @DisplayName("trata soma nula do banco como zero")
    void trataSomaNulaComoZero() {
        when(transactions.sumByType(any(), any(), any(), any())).thenReturn(null);

        AnalyticsService.AnalyticsSummary resumo = service.getSummary(contaId);

        assertThat(resumo.totalIncome()).isEqualByComparingTo("0");
        assertThat(resumo.totalExpense()).isEqualByComparingTo("0");
        assertThat(resumo.balance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("preserva a ordem decrescente das despesas por categoria")
    void preservaOrdemDasCategorias() {
        when(transactions.sumExpensesByCategory(contaId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)))
            .thenReturn(List.<Object[]>of(
                new Object[] {"Moradia", new BigDecimal("1500.00")},
                new Object[] {"Alimentacao", new BigDecimal("620.30")},
                new Object[] {"Transporte", new BigDecimal("180.00")}));

        AnalyticsService.AnalyticsSummary resumo = service.getSummary(contaId);

        assertThat(resumo.expensesByCategory())
            .containsExactly(
                entry("Moradia", new BigDecimal("1500.00")),
                entry("Alimentacao", new BigDecimal("620.30")),
                entry("Transporte", new BigDecimal("180.00")));
    }

    @Test
    @DisplayName("trata valor nulo numa linha de categoria como zero")
    void trataValorNuloDeCategoria() {
        when(transactions.sumExpensesByCategory(any(), any(), any()))
            .thenReturn(List.<Object[]>of(new Object[] {"Moradia", null}));

        assertThat(service.getSummary(contaId).expensesByCategory())
            .containsEntry("Moradia", BigDecimal.ZERO);
    }

    /**
     * Cenario que era inalcancavel sem o {@link Clock}: confirma que a janela de
     * apuracao e sempre [dia 1 do mes, dia 1 do mes seguinte), inclusive na virada
     * de ano e em fevereiro de ano bissexto.
     */
    @ParameterizedTest(name = "em {0}, apura de {1} a {2}")
    @CsvSource({
        "2026-08-10T15:30:00Z, 2026-08-01, 2026-09-01",
        "2026-08-01T00:00:00Z, 2026-08-01, 2026-09-01",
        "2026-08-31T23:59:59Z, 2026-08-01, 2026-09-01",
        "2026-12-15T12:00:00Z, 2026-12-01, 2027-01-01",
        "2028-02-29T12:00:00Z, 2028-02-01, 2028-03-01",
        "2026-01-01T00:00:00Z, 2026-01-01, 2026-02-01"
    })
    @DisplayName("a janela de apuracao acompanha o mes do relogio")
    void janelaAcompanhaOMes(Instant agora, LocalDate inicioEsperado, LocalDate fimEsperado) {
        serviceEm(agora).getSummary(contaId);

        verify(transactions).sumByType(contaId, inicioEsperado, fimEsperado, CategoryType.INCOME);
        verify(transactions).sumByType(contaId, inicioEsperado, fimEsperado, CategoryType.EXPENSE);
        verify(transactions).sumExpensesByCategory(contaId, inicioEsperado, fimEsperado);
    }

    /**
     * B-03 da auditoria: o campo {@code balance} e o resultado do mes corrente, nao
     * o saldo da conta. No dia 1o de cada mes ele "zera", e no dia 2 quem gastou
     * R$ 50 aparece com saldo negativo. Fixa o comportamento para que a separacao
     * entre "saldo da conta" e "resultado do mes" seja uma mudanca deliberada.
     */
    @Test
    @DisplayName("B-03: balance e o resultado do mes, nao o saldo acumulado da conta")
    void balanceEhResultadoDoMesNaoSaldoAcumulado() {
        when(transactions.sumByType(contaId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), CategoryType.INCOME))
            .thenReturn(BigDecimal.ZERO);
        when(transactions.sumByType(contaId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), CategoryType.EXPENSE))
            .thenReturn(new BigDecimal("50.00"));

        assertThat(service.getSummary(contaId).balance())
            .as("sem nenhuma entrada no mes, o saldo fica negativo mesmo com dinheiro na conta")
            .isEqualByComparingTo("-50.00");
    }
}
