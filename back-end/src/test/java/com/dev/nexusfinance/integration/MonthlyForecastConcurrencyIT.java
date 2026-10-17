package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.MonthlyForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concorrencia no planejamento mensal (QA-11).
 *
 * <p>Esta e a parte mais sofisticada do codigo do projeto e nao tinha nenhuma
 * verificacao: {@code MonthlyForecast} e {@code PlannedExpense} usam
 * {@code @Version} (lock otimista) e o service pega um
 * {@code LockModeType.PESSIMISTIC_WRITE} na linha do usuario antes de escrever.
 *
 * <p>Esse desenho so pode ser testado com banco real e threads de verdade: o
 * {@code EntityManager} mockado devolve o usuario na hora, sem bloquear nada.
 * O cenario que importa — duas requisicoes do mesmo usuario no mesmo mes, que e
 * o que acontece quando alguem clica duas vezes — nunca havia sido exercitado.
 */
class MonthlyForecastConcurrencyIT extends AbstractIntegrationIT {
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);

    @Autowired
    private MonthlyForecastService forecasts;

    @Autowired
    private TestDataFactory dados;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User ana;

    @BeforeEach
    void prepararUsuario() {
        jdbcTemplate.update("delete from tb_planned_expenses");
        jdbcTemplate.update("delete from tb_monthly_forecasts");
        dados.limparTudo();
        ana = dados.usuario("Ana Silva", "ana@email.com", "12345678901");
    }

    /**
     * O cenario do duplo clique. Sem o bloqueio pessimista no usuario, as duas
     * transacoes correriam o {@code getOrCreate} em paralelo, as duas achariam o
     * mes vazio, e as duas tentariam inserir — a UNIQUE
     * {@code uk_forecast_user_month} faria uma delas estourar.
     */
    @Test
    @DisplayName("duas escritas simultaneas no mesmo mes resultam em um unico planejamento")
    void escritasSimultaneasCriamUmUnicoPlanejamento() throws Exception {
        List<Throwable> falhas = executarEmParalelo(2, () ->
            forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("5000.00")));

        assertThat(contarPlanejamentos())
            .as("a UNIQUE uk_forecast_user_month deve resultar em exatamente uma linha")
            .isEqualTo(1);
        assertThat(falhas)
            .as("o bloqueio pessimista deve serializar as escritas, sem erro para o usuario")
            .isEmpty();
    }

    @Test
    @DisplayName("cinco adicoes simultaneas de gasto preservam todas as linhas")
    void adicoesSimultaneasPreservamTodasAsLinhas() throws Exception {
        forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("10000.00"));
        AtomicInteger contador = new AtomicInteger();

        List<Throwable> falhas = executarEmParalelo(5, () -> forecasts.addExpense(
            ana.getIdUser(), AGOSTO, new MonthlyForecastService.ExpenseInput(
                "Gasto " + contador.incrementAndGet(), new BigDecimal("100.00"), null)));

        assertThat(falhas).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from tb_planned_expenses", Integer.class))
            .as("nenhuma adicao pode ser perdida por sobrescrita")
            .isEqualTo(5);
    }

    @Test
    @DisplayName("o total projetado fica consistente depois de escritas concorrentes")
    void totalConsistenteAposConcorrencia() throws Exception {
        forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("10000.00"));
        AtomicInteger contador = new AtomicInteger();

        executarEmParalelo(4, () -> forecasts.addExpense(
            ana.getIdUser(), AGOSTO, new MonthlyForecastService.ExpenseInput(
                "Gasto " + contador.incrementAndGet(), new BigDecimal("250.00"), null)));

        MonthlyForecastService.ForecastSummary resumo = forecasts.get(ana.getIdUser(), AGOSTO);

        assertThat(resumo.totalPlanned()).isEqualByComparingTo("1000.00");
        assertThat(resumo.projectedBalance()).isEqualByComparingTo("9000.00");
        assertThat(resumo.expenses()).hasSize(4);
    }

    @Test
    @DisplayName("usuarios diferentes escrevem no mesmo mes sem interferencia")
    void usuariosDiferentesNaoInterferem() throws Exception {
        User bruno = dados.usuario("Bruno Souza", "bruno@email.com", "98765432100");

        forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("5000.00"));
        forecasts.updateIncome(bruno.getIdUser(), AGOSTO, new BigDecimal("3000.00"));

        assertThat(forecasts.get(ana.getIdUser(), AGOSTO).expectedIncome())
            .isEqualByComparingTo("5000.00");
        assertThat(forecasts.get(bruno.getIdUser(), AGOSTO).expectedIncome())
            .isEqualByComparingTo("3000.00");
        assertThat(contarPlanejamentos()).isEqualTo(2);
    }

    @Test
    @DisplayName("a coluna version e incrementada a cada escrita (lock otimista ativo)")
    void versionEhIncrementada() {
        forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("5000.00"));
        long primeira = versaoDoPlanejamento();

        forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("6000.00"));
        long segunda = versaoDoPlanejamento();

        assertThat(segunda)
            .as("@Version precisa estar realmente mapeada na coluna do banco")
            .isGreaterThan(primeira);
    }

    @Test
    @DisplayName("remover gasto apaga a linha filha, respeitando orphanRemoval")
    void removerGastoApagaALinhaFilha() {
        forecasts.updateIncome(ana.getIdUser(), AGOSTO, new BigDecimal("5000.00"));
        MonthlyForecastService.ForecastSummary comGasto = forecasts.addExpense(
            ana.getIdUser(), AGOSTO,
            new MonthlyForecastService.ExpenseInput("Aluguel", new BigDecimal("1500.00"), null));

        forecasts.deleteExpense(ana.getIdUser(), AGOSTO, comGasto.expenses().get(0).id());

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from tb_planned_expenses", Integer.class))
            .as("orphanRemoval deve apagar a linha, nao apenas soltar a FK")
            .isZero();
    }

    private List<Throwable> executarEmParalelo(int threads, Callable<?> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futuros.add(executor.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }

        largada.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS))
            .as("as threads nao podem ficar presas em deadlock")
            .isTrue();

        List<Throwable> falhas = new ArrayList<>();
        for (Future<?> futuro : futuros) {
            try {
                futuro.get();
            } catch (Exception exception) {
                falhas.add(exception.getCause() == null ? exception : exception.getCause());
            }
        }
        return falhas;
    }

    private Integer contarPlanejamentos() {
        return jdbcTemplate.queryForObject("select count(*) from tb_monthly_forecasts", Integer.class);
    }

    private long versaoDoPlanejamento() {
        return jdbcTemplate.queryForObject(
            "select version from tb_monthly_forecasts limit 1", Long.class);
    }
}
