package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.MonthlyForecast;
import com.dev.nexusfinance.models.PlannedExpense;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.MonthlyForecastRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyForecastServiceTest {
    private MonthlyForecastRepository forecasts;
    private EntityManager entityManager;
    private MonthlyForecastService service;
    private User ana;
    private final YearMonth agosto = YearMonth.of(2026, 8);

    @BeforeEach
    void setUp() {
        forecasts = mock(MonthlyForecastRepository.class);
        entityManager = mock(EntityManager.class);
        service = new MonthlyForecastService(forecasts, entityManager);

        ana = UserFixture.ana();
        when(entityManager.find(User.class, ana.getIdUser(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(ana);
        when(forecasts.saveAndFlush(any(MonthlyForecast.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("consulta")
    class Consulta {

        @Test
        @DisplayName("retorna mes vazio sem criar registro")
        void retornaMesVazioSemCriarRegistro() {
            semPlanejamento();

            MonthlyForecastService.ForecastSummary result = service.get(ana.getIdUser(), agosto);

            assertThat(result.month()).isEqualTo("2026-08");
            assertThat(result.expectedIncome()).isEqualByComparingTo("0.00");
            assertThat(result.totalPlanned()).isEqualByComparingTo("0.00");
            assertThat(result.projectedBalance()).isEqualByComparingTo("0.00");
            assertThat(result.expenses()).isEmpty();
            verify(forecasts, never()).save(any());
            verify(forecasts, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("calcula total e saldo projetado no servidor")
        void calculaTotalESaldoProjetado() {
            MonthlyForecast forecast = planejamento("5000.00");
            forecast.addExpense(gasto("Aluguel", "1500.00", LocalDate.of(2026, 8, 5)));
            forecast.addExpense(gasto("Cartão", "850.50", LocalDate.of(2026, 8, 10)));
            comPlanejamento(forecast);

            MonthlyForecastService.ForecastSummary result = service.get(ana.getIdUser(), agosto);

            assertThat(result.totalPlanned()).isEqualByComparingTo("2350.50");
            assertThat(result.projectedBalance()).isEqualByComparingTo("2649.50");
        }

        @Test
        @DisplayName("ordena gastos por vencimento, com os sem data no fim")
        void ordenaGastosPorVencimento() {
            MonthlyForecast forecast = planejamento("5000.00");
            forecast.addExpense(gasto("Sem data", "100.00", null));
            forecast.addExpense(gasto("Dia 20", "200.00", LocalDate.of(2026, 8, 20)));
            forecast.addExpense(gasto("Dia 5", "300.00", LocalDate.of(2026, 8, 5)));
            comPlanejamento(forecast);

            assertThat(service.get(ana.getIdUser(), agosto).expenses())
                .extracting(MonthlyForecastService.ExpenseView::description)
                .containsExactly("Dia 5", "Dia 20", "Sem data");
        }

        @Test
        @DisplayName("desempata gastos da mesma data pela descricao, ignorando caixa")
        void desempataPelaDescricao() {
            MonthlyForecast forecast = planejamento("5000.00");
            LocalDate mesmoDia = LocalDate.of(2026, 8, 5);
            forecast.addExpense(gasto("zebra", "100.00", mesmoDia));
            forecast.addExpense(gasto("Alfa", "200.00", mesmoDia));
            comPlanejamento(forecast);

            assertThat(service.get(ana.getIdUser(), agosto).expenses())
                .extracting(MonthlyForecastService.ExpenseView::description)
                .containsExactly("Alfa", "zebra");
        }

        @Test
        @DisplayName("exige o mes na consulta")
        void exigeMes() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.get(ana.getIdUser(), null))
                .withMessage("Mês é obrigatório");
        }

        @Test
        @DisplayName("consulta nao bloqueia o usuario")
        void consultaNaoBloqueiaUsuario() {
            semPlanejamento();

            service.get(ana.getIdUser(), agosto);

            verify(entityManager, never()).find(any(), any(), any(LockModeType.class));
        }
    }

    @Nested
    @DisplayName("renda prevista")
    class RendaPrevista {

        @Test
        @DisplayName("cria o mes com bloqueio pessimista e precisao monetaria")
        void criaRendaComBloqueioEPrecisao() {
            semPlanejamento();

            MonthlyForecastService.ForecastSummary result =
                service.updateIncome(ana.getIdUser(), agosto, new BigDecimal("4200.5"));

            assertThat(result.expectedIncome()).isEqualByComparingTo("4200.50");
            verify(entityManager).find(User.class, ana.getIdUser(), LockModeType.PESSIMISTIC_WRITE);
            verify(forecasts).saveAndFlush(any(MonthlyForecast.class));
        }

        @Test
        @DisplayName("atualiza a renda de um mes que ja existe")
        void atualizaRendaDeMesExistente() {
            comPlanejamento(planejamento("3000.00"));

            assertThat(service.updateIncome(ana.getIdUser(), agosto, new BigDecimal("7500.00"))
                .expectedIncome()).isEqualByComparingTo("7500.00");
        }

        @Test
        @DisplayName("aceita renda zero")
        void aceitaRendaZero() {
            semPlanejamento();

            assertThat(service.updateIncome(ana.getIdUser(), agosto, BigDecimal.ZERO)
                .expectedIncome()).isEqualByComparingTo("0.00");
        }

        @ParameterizedTest(name = "renda invalida: {0} -> \"{1}\"")
        @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "NULL                    | Renda prevista é obrigatória",
            "10.001                  | Renda prevista deve ter no máximo duas casas decimais",
            "-1.00                   | Renda prevista não pode ser negativa",
            "100000000000000000.00   | Renda prevista excede o limite permitido"
        })
        @DisplayName("rejeita renda invalida")
        void rejeitaRendaInvalida(BigDecimal valor, String mensagem) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateIncome(ana.getIdUser(), agosto, valor))
                .withMessage(mensagem);
            verify(forecasts, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("exige usuario existente, mesmo com token valido")
        void exigeUsuarioExistente() {
            UUID fantasma = UUID.randomUUID();
            when(entityManager.find(User.class, fantasma, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.updateIncome(fantasma, agosto, BigDecimal.TEN))
                .withMessage("Usuário não encontrado");
        }
    }

    @Nested
    @DisplayName("gastos previstos")
    class GastosPrevistos {

        @Test
        @DisplayName("adiciona gasto e recalcula o total")
        void adicionaGasto() {
            comPlanejamento(planejamento("5000.00"));

            MonthlyForecastService.ForecastSummary result = service.addExpense(ana.getIdUser(), agosto,
                new MonthlyForecastService.ExpenseInput("  Aluguel  ", new BigDecimal("1500.00"),
                    LocalDate.of(2026, 8, 5)));

            assertThat(result.totalPlanned()).isEqualByComparingTo("1500.00");
            assertThat(result.projectedBalance()).isEqualByComparingTo("3500.00");
            assertThat(result.expenses()).singleElement()
                .extracting(MonthlyForecastService.ExpenseView::description)
                .isEqualTo("Aluguel");
        }

        @Test
        @DisplayName("cria o planejamento do mes ao adicionar o primeiro gasto")
        void criaPlanejamentoNoPrimeiroGasto() {
            semPlanejamento();

            MonthlyForecastService.ForecastSummary result = service.addExpense(ana.getIdUser(), agosto,
                new MonthlyForecastService.ExpenseInput("Aluguel", new BigDecimal("1500.00"), null));

            assertThat(result.expectedIncome()).isEqualByComparingTo("0.00");
            assertThat(result.projectedBalance()).isEqualByComparingTo("-1500.00");
        }

        @Test
        @DisplayName("aceita gasto sem data de vencimento")
        void aceitaGastoSemVencimento() {
            comPlanejamento(planejamento("5000.00"));

            assertThat(service.addExpense(ana.getIdUser(), agosto,
                new MonthlyForecastService.ExpenseInput("Imprevistos", new BigDecimal("200.00"), null))
                .expenses()).singleElement()
                .extracting(MonthlyForecastService.ExpenseView::dueDate)
                .isNull();
        }

        @ParameterizedTest(name = "gasto invalido: {0} -> \"{3}\"")
        @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "descricao nula    | NULL    | 10.00  | Descrição do gasto é obrigatória",
            "descricao vazia   | ''      | 10.00  | Descrição do gasto é obrigatória",
            "descricao branca  | '   '   | 10.00  | Descrição do gasto é obrigatória",
            "valor nulo        | Aluguel | NULL   | Valor previsto é obrigatória",
            "valor zero        | Aluguel | 0      | Valor previsto deve ser maior que zero",
            "valor negativo    | Aluguel | -10.00 | Valor previsto deve ser maior que zero",
            "tres decimais     | Aluguel | 10.001 | Valor previsto deve ter no máximo duas casas decimais"
        })
        @DisplayName("rejeita gasto invalido")
        void rejeitaGastoInvalido(String caso, String descricao, BigDecimal valor, String mensagem) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addExpense(ana.getIdUser(), agosto,
                    new MonthlyForecastService.ExpenseInput(descricao, valor, null)))
                .withMessage(mensagem);
        }

        @Test
        @DisplayName("rejeita entrada nula")
        void rejeitaEntradaNula() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addExpense(ana.getIdUser(), agosto, null))
                .withMessage("Descrição do gasto é obrigatória");
        }

        @Test
        @DisplayName("rejeita descricao acima de 120 caracteres")
        void rejeitaDescricaoLonga() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addExpense(ana.getIdUser(), agosto,
                    new MonthlyForecastService.ExpenseInput("A".repeat(121), BigDecimal.TEN, null)))
                .withMessage("Descrição deve ter no máximo 120 caracteres");
        }

        @Test
        @DisplayName("aceita descricao de exatamente 120 caracteres")
        void aceitaDescricaoNoLimite() {
            comPlanejamento(planejamento("5000.00"));

            assertThat(service.addExpense(ana.getIdUser(), agosto,
                new MonthlyForecastService.ExpenseInput("A".repeat(120), BigDecimal.TEN, null))
                .expenses()).singleElement()
                .extracting(MonthlyForecastService.ExpenseView::description)
                .isEqualTo("A".repeat(120));
        }

        @ParameterizedTest(name = "vencimento fora do mes: {0}")
        @ValueSource(strings = {"2026-07-31", "2026-09-01", "2025-08-15"})
        @DisplayName("rejeita vencimento que nao pertence ao mes selecionado")
        void rejeitaVencimentoDeOutroMes(LocalDate foraDoMes) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addExpense(ana.getIdUser(), agosto,
                    new MonthlyForecastService.ExpenseInput("Aluguel", BigDecimal.TEN, foraDoMes)))
                .withMessage("Vencimento deve pertencer ao mês selecionado");
        }

        @ParameterizedTest(name = "vencimento valido na borda: {0}")
        @ValueSource(strings = {"2026-08-01", "2026-08-31"})
        @DisplayName("aceita vencimento no primeiro e no ultimo dia do mes")
        void aceitaVencimentoNasBordasDoMes(LocalDate borda) {
            comPlanejamento(planejamento("5000.00"));

            assertThat(service.addExpense(ana.getIdUser(), agosto,
                new MonthlyForecastService.ExpenseInput("Aluguel", BigDecimal.TEN, borda))
                .expenses()).hasSize(1);
        }

        @Test
        @DisplayName("atualiza gasto existente")
        void atualizaGastoExistente() {
            MonthlyForecast forecast = planejamento("5000.00");
            PlannedExpense existente = gasto("Aluguel", "1000.00", null);
            forecast.addExpense(existente);
            comPlanejamento(forecast);

            MonthlyForecastService.ForecastSummary result = service.updateExpense(
                ana.getIdUser(), agosto, existente.getIdPlannedExpense(),
                new MonthlyForecastService.ExpenseInput("Aluguel reajustado",
                    new BigDecimal("1200.00"), LocalDate.of(2026, 8, 10)));

            assertThat(result.expenses()).singleElement().satisfies(view -> {
                assertThat(view.description()).isEqualTo("Aluguel reajustado");
                assertThat(view.amount()).isEqualByComparingTo("1200.00");
                assertThat(view.dueDate()).isEqualTo(LocalDate.of(2026, 8, 10));
            });
        }

        @Test
        @DisplayName("nao atualiza gasto que nao pertence ao usuario e mes")
        void naoAtualizaGastoDeOutroEscopo() {
            MonthlyForecast forecast = planejamento("3000.00");
            forecast.addExpense(gasto("Aluguel", "1000.00", null));
            comPlanejamento(forecast);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.updateExpense(ana.getIdUser(), agosto, UUID.randomUUID(),
                    new MonthlyForecastService.ExpenseInput("Cartão", new BigDecimal("500.00"), null)))
                .withMessage("Gasto previsto não encontrado");
        }

        @Test
        @DisplayName("nao encontra planejamento de outro usuario pelo id do gasto")
        void naoVazaPlanejamentoDeOutroUsuario() {
            User bruno = UserFixture.bruno();
            when(entityManager.find(User.class, bruno.getIdUser(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(bruno);
            when(forecasts.findByUserAndMonth(bruno.getIdUser(), agosto.atDay(1)))
                .thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.updateExpense(bruno.getIdUser(), agosto, UUID.randomUUID(),
                    new MonthlyForecastService.ExpenseInput("Cartão", new BigDecimal("500.00"), null)))
                .withMessage("Planejamento mensal não encontrado");

            verify(forecasts).findByUserAndMonth(bruno.getIdUser(), agosto.atDay(1));
        }

        @Test
        @DisplayName("valida a entrada antes de bloquear o usuario")
        void validaAntesDeBloquear() {
            assertThatIllegalArgumentException().isThrownBy(() -> service.updateExpense(
                ana.getIdUser(), agosto, UUID.randomUUID(),
                new MonthlyForecastService.ExpenseInput(null, BigDecimal.TEN, null)));

            verify(entityManager, never()).find(any(), any(), any(LockModeType.class));
        }

        @Test
        @DisplayName("remove gasto e recalcula o total")
        void removeGasto() {
            MonthlyForecast forecast = planejamento("5000.00");
            PlannedExpense aluguel = gasto("Aluguel", "1000.00", null);
            PlannedExpense cartao = gasto("Cartão", "500.00", null);
            forecast.addExpense(aluguel);
            forecast.addExpense(cartao);
            comPlanejamento(forecast);

            service.deleteExpense(ana.getIdUser(), agosto, aluguel.getIdPlannedExpense());

            assertThat(forecast.getExpenses()).containsExactly(cartao);
            assertThat(aluguel.getForecast()).isNull();
            verify(forecasts).saveAndFlush(forecast);
        }

        @Test
        @DisplayName("nao remove gasto de outro escopo")
        void naoRemoveGastoDeOutroEscopo() {
            MonthlyForecast forecast = planejamento("5000.00");
            forecast.addExpense(gasto("Aluguel", "1000.00", null));
            comPlanejamento(forecast);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deleteExpense(ana.getIdUser(), agosto, UUID.randomUUID()))
                .withMessage("Gasto previsto não encontrado");
        }

        @Test
        @DisplayName("exige planejamento existente para remover gasto")
        void exigePlanejamentoParaRemover() {
            semPlanejamento();

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.deleteExpense(ana.getIdUser(), agosto, UUID.randomUUID()))
                .withMessage("Planejamento mensal não encontrado");
        }

        @Test
        @DisplayName("bloqueia o usuario em toda escrita de gasto")
        void bloqueiaUsuarioNaEscrita() {
            comPlanejamento(planejamento("5000.00"));

            service.addExpense(ana.getIdUser(), agosto,
                new MonthlyForecastService.ExpenseInput("Aluguel", BigDecimal.TEN, null));

            verify(entityManager).find(User.class, ana.getIdUser(), LockModeType.PESSIMISTIC_WRITE);
        }
    }

    private void semPlanejamento() {
        when(forecasts.findByUserAndMonth(ana.getIdUser(), agosto.atDay(1))).thenReturn(Optional.empty());
    }

    private void comPlanejamento(MonthlyForecast forecast) {
        when(forecasts.findByUserAndMonth(ana.getIdUser(), agosto.atDay(1))).thenReturn(Optional.of(forecast));
    }

    private MonthlyForecast planejamento(String renda) {
        MonthlyForecast forecast = new MonthlyForecast();
        forecast.setIdForecast(UUID.randomUUID());
        forecast.setUser(ana);
        forecast.setReferenceMonth(agosto.atDay(1));
        forecast.setExpectedIncome(new BigDecimal(renda));
        return forecast;
    }

    private PlannedExpense gasto(String descricao, String valor, LocalDate vencimento) {
        PlannedExpense expense = new PlannedExpense();
        expense.setIdPlannedExpense(UUID.randomUUID());
        expense.setDescription(descricao);
        expense.setAmount(new BigDecimal(valor));
        expense.setDueDate(vencimento);
        return expense;
    }
}
