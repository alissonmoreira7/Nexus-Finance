package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.dto.UserView;
import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.fixtures.FinanceFixture;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.AccountService;
import com.dev.nexusfinance.services.AnalyticsService;
import com.dev.nexusfinance.services.AuthService;
import com.dev.nexusfinance.services.CategoryService;
import com.dev.nexusfinance.services.MonthlyForecastService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slices dos controllers restantes: contas, analytics, categorias, planejamento
 * mensal e autenticacao. Agrupados numa classe por {@code @Nested} porque cada um
 * tem poucos endpoints — evita cinco contextos Spring quase identicos.
 */
class RemainingControllersTest {
    private static final String ATRIBUTO_USUARIO = "authenticatedUserId";

    @Nested
    @WebMvcTest(controllers = AccountController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("test")
    @DisplayName("AccountController")
    class Contas {
        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AccountService service;

        @MockitoBean
        private AuthService authService;

        @Test
        @DisplayName("POST /accounts devolve 201 com a conta criada")
        void criaConta() throws Exception {
            User ana = UserFixture.ana();
            Account conta = FinanceFixture.conta(ana);
            when(service.create(ana.getIdUser(), "Banco do Brasil")).thenReturn(conta);

            mockMvc.perform(post("/api/v1/accounts")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bankName\":\"Banco do Brasil\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(conta.getIdAccount().toString()))
                .andExpect(jsonPath("$.bankName").value("Banco Teste"))
                .andExpect(jsonPath("$.userId").value(ana.getIdUser().toString()));
        }

        @Test
        @DisplayName("POST /accounts propaga 400 de nome de banco ausente")
        void rejeitaNomeAusente() throws Exception {
            User ana = UserFixture.ana();
            when(service.create(any(), any()))
                .thenThrow(new IllegalArgumentException("Nome do banco é obrigatório"));

            mockMvc.perform(post("/api/v1/accounts")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bankName\":\"  \"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /accounts/user/{id} devolve as contas do proprio usuario")
        void listaContasProprias() throws Exception {
            User ana = UserFixture.ana();
            when(service.findByUser(ana.getIdUser())).thenReturn(List.of(FinanceFixture.conta(ana)));

            mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
        }

        @Test
        @DisplayName("GET /accounts/user/{id} recusa listar contas de outro usuario")
        void recusaListarContasDeOutro() throws Exception {
            User ana = UserFixture.ana();
            User bruno = UserFixture.bruno();

            mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                    .requestAttr(ATRIBUTO_USUARIO, bruno.getIdUser()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Acesso negado"));

            verify(service, never()).findByUser(any());
        }

        @Test
        @DisplayName("DELETE /accounts/{id} verifica dono e devolve 204")
        void excluiContaPropria() throws Exception {
            User ana = UserFixture.ana();
            UUID contaId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/accounts/{id}", contaId)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isNoContent());

            verify(service).assertOwnership(contaId, ana.getIdUser());
            verify(service).delete(contaId);
        }

        @Test
        @DisplayName("DELETE /accounts/{id} de conta alheia nao exclui")
        void naoExcluiContaAlheia() throws Exception {
            User bruno = UserFixture.bruno();
            UUID contaId = UUID.randomUUID();
            doThrow(new UnauthorizedException("Acesso negado a esta conta"))
                .when(service).assertOwnership(contaId, bruno.getIdUser());

            mockMvc.perform(delete("/api/v1/accounts/{id}", contaId)
                    .requestAttr(ATRIBUTO_USUARIO, bruno.getIdUser()))
                .andExpect(status().isUnauthorized());

            verify(service, never()).delete(any());
        }
    }

    @Nested
    @WebMvcTest(controllers = AnalyticsController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("test")
    @DisplayName("AnalyticsController")
    class Analytics {
        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AnalyticsService service;

        @MockitoBean
        private AccountService accounts;

        @MockitoBean
        private AuthService authService;

        @Test
        @DisplayName("GET /{id}/summary devolve o resumo e verifica o dono")
        void devolveResumo() throws Exception {
            User ana = UserFixture.ana();
            UUID contaId = UUID.randomUUID();
            LinkedHashMap<String, BigDecimal> porCategoria = new LinkedHashMap<>();
            porCategoria.put("Moradia", new BigDecimal("1500.00"));
            when(service.getSummary(contaId)).thenReturn(new AnalyticsService.AnalyticsSummary(
                new BigDecimal("5000.00"), new BigDecimal("1850.50"),
                new BigDecimal("3149.50"), porCategoria));

            mockMvc.perform(get("/api/v1/analytics/{id}/summary", contaId)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(5000.00))
                .andExpect(jsonPath("$.totalExpense").value(1850.50))
                .andExpect(jsonPath("$.balance").value(3149.50))
                .andExpect(jsonPath("$.expensesByCategory.Moradia").value(1500.00));

            verify(accounts).assertOwnership(contaId, ana.getIdUser());
        }

        @Test
        @DisplayName("GET /{id}/summary de conta alheia nao consulta o analytics")
        void recusaContaAlheia() throws Exception {
            User bruno = UserFixture.bruno();
            UUID contaId = UUID.randomUUID();
            doThrow(new UnauthorizedException("Acesso negado a esta conta"))
                .when(accounts).assertOwnership(contaId, bruno.getIdUser());

            mockMvc.perform(get("/api/v1/analytics/{id}/summary", contaId)
                    .requestAttr(ATRIBUTO_USUARIO, bruno.getIdUser()))
                .andExpect(status().isUnauthorized());

            verify(service, never()).getSummary(any());
        }

        @Test
        @DisplayName("GET /{id}/summary devolve 404 para conta inexistente")
        void contaInexistenteDevolve404() throws Exception {
            User ana = UserFixture.ana();
            UUID contaId = UUID.randomUUID();
            doThrow(new ResourceNotFoundException("Conta não encontrada"))
                .when(accounts).assertOwnership(contaId, ana.getIdUser());

            mockMvc.perform(get("/api/v1/analytics/{id}/summary", contaId)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @WebMvcTest(controllers = CategoryController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("test")
    @DisplayName("CategoryController")
    class Categorias {
        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private CategoryService service;

        @MockitoBean
        private AuthService authService;

        @Test
        @DisplayName("GET /categories lista as categorias")
        void listaCategorias() throws Exception {
            when(service.findAll()).thenReturn(FinanceFixture.dicionarioPadrao());

            mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("Transporte"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"));
        }

        @Test
        @DisplayName("POST /categories devolve 201")
        void criaCategoria() throws Exception {
            Category nova = FinanceFixture.categoria("Lazer", CategoryType.EXPENSE, "CINEMA");
            when(service.create(any(Category.class))).thenReturn(nova);

            mockMvc.perform(post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Lazer\",\"type\":\"EXPENSE\",\"keywords\":\"CINEMA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lazer"));
        }

        @Test
        @DisplayName("POST /categories propaga 400 de categoria duplicada")
        void rejeitaDuplicada() throws Exception {
            when(service.create(any(Category.class)))
                .thenThrow(new IllegalArgumentException("Categoria já cadastrada"));

            mockMvc.perform(post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Lazer\",\"type\":\"EXPENSE\",\"keywords\":\"CINEMA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Categoria já cadastrada"));
        }

        @Test
        @DisplayName("POST /categories devolve 400 para tipo invalido no JSON")
        void rejeitaTipoInvalido() throws Exception {
            mockMvc.perform(post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Lazer\",\"type\":\"NAO_EXISTE\",\"keywords\":\"CINEMA\"}"))
                .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @WebMvcTest(controllers = MonthlyForecastController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("test")
    @DisplayName("MonthlyForecastController")
    class Planejamento {
        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private MonthlyForecastService service;

        @MockitoBean
        private AuthService authService;

        private MonthlyForecastService.ForecastSummary resumoVazio() {
            BigDecimal zero = new BigDecimal("0.00");
            return new MonthlyForecastService.ForecastSummary("2026-08", zero, zero, zero, List.of());
        }

        @Test
        @DisplayName("GET /{month} devolve o resumo do mes")
        void devolveResumoDoMes() throws Exception {
            User ana = UserFixture.ana();
            when(service.get(ana.getIdUser(), YearMonth.of(2026, 8))).thenReturn(
                new MonthlyForecastService.ForecastSummary("2026-08",
                    new BigDecimal("5000.00"), new BigDecimal("2350.50"), new BigDecimal("2649.50"),
                    List.of(new MonthlyForecastService.ExpenseView(
                        UUID.randomUUID(), "Aluguel", new BigDecimal("1500.00"),
                        LocalDate.of(2026, 8, 5)))));

            mockMvc.perform(get("/api/v1/forecasts/{month}", "2026-08")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.projectedBalance").value(2649.50))
                .andExpect(jsonPath("$.expenses[0].description").value("Aluguel"));
        }

        @ParameterizedTest(name = "mes invalido: [{0}]")
        @ValueSource(strings = {"2026-13", "agosto", "2026", "26-08", "2026-00", "2026-8-10"})
        @DisplayName("GET /{month} devolve 400 para mes fora do formato AAAA-MM")
        void rejeitaMesInvalido(String mesInvalido) throws Exception {
            User ana = UserFixture.ana();

            mockMvc.perform(get("/api/v1/forecasts/{month}", mesInvalido)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mês inválido. Use o formato AAAA-MM"));

            verify(service, never()).get(any(), any());
        }

        @Test
        @DisplayName("PUT /{month} atualiza a renda prevista")
        void atualizaRenda() throws Exception {
            User ana = UserFixture.ana();
            when(service.updateIncome(eq(ana.getIdUser()), eq(YearMonth.of(2026, 8)), any()))
                .thenReturn(resumoVazio());

            mockMvc.perform(put("/api/v1/forecasts/{month}", "2026-08")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedIncome\":4200.50}"))
                .andExpect(status().isOk());

            verify(service).updateIncome(ana.getIdUser(), YearMonth.of(2026, 8), new BigDecimal("4200.50"));
        }

        @Test
        @DisplayName("PUT /{month} com corpo vazio repassa renda nula ao service")
        void corpoVazioRepassaNulo() throws Exception {
            User ana = UserFixture.ana();
            when(service.updateIncome(any(), any(), isNull())).thenReturn(resumoVazio());

            mockMvc.perform(put("/api/v1/forecasts/{month}", "2026-08")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk());

            verify(service).updateIncome(ana.getIdUser(), YearMonth.of(2026, 8), null);
        }

        @Test
        @DisplayName("POST /{month}/expenses devolve 201")
        void adicionaGasto() throws Exception {
            User ana = UserFixture.ana();
            when(service.addExpense(any(), any(), any())).thenReturn(resumoVazio());

            mockMvc.perform(post("/api/v1/forecasts/{month}/expenses", "2026-08")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Aluguel\",\"amount\":1500.00,\"dueDate\":\"2026-08-05\"}"))
                .andExpect(status().isCreated());

            verify(service).addExpense(ana.getIdUser(), YearMonth.of(2026, 8),
                new MonthlyForecastService.ExpenseInput("Aluguel", new BigDecimal("1500.00"),
                    LocalDate.of(2026, 8, 5)));
        }

        @Test
        @DisplayName("PUT /{month}/expenses/{id} atualiza o gasto")
        void atualizaGasto() throws Exception {
            User ana = UserFixture.ana();
            UUID gastoId = UUID.randomUUID();
            when(service.updateExpense(any(), any(), any(), any())).thenReturn(resumoVazio());

            mockMvc.perform(put("/api/v1/forecasts/{month}/expenses/{id}", "2026-08", gastoId)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\":\"Aluguel\",\"amount\":1200.00}"))
                .andExpect(status().isOk());

            verify(service).updateExpense(eq(ana.getIdUser()), eq(YearMonth.of(2026, 8)),
                eq(gastoId), any());
        }

        @Test
        @DisplayName("DELETE /{month}/expenses/{id} devolve 204")
        void removeGasto() throws Exception {
            User ana = UserFixture.ana();
            UUID gastoId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/forecasts/{month}/expenses/{id}", "2026-08", gastoId)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isNoContent());

            verify(service).deleteExpense(ana.getIdUser(), YearMonth.of(2026, 8), gastoId);
        }

        @Test
        @DisplayName("DELETE /{month}/expenses/{id} propaga 404 de gasto inexistente")
        void removeGastoInexistente() throws Exception {
            User ana = UserFixture.ana();
            UUID gastoId = UUID.randomUUID();
            doThrow(new ResourceNotFoundException("Gasto previsto não encontrado"))
                .when(service).deleteExpense(any(), any(), any());

            mockMvc.perform(delete("/api/v1/forecasts/{month}/expenses/{id}", "2026-08", gastoId)
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("o planejamento sempre usa o usuario do token, nunca um id da URL")
        void usaSempreOUsuarioDoToken() throws Exception {
            User ana = UserFixture.ana();
            when(service.get(any(), any())).thenReturn(resumoVazio());

            mockMvc.perform(get("/api/v1/forecasts/{month}", "2026-08")
                    .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                    .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

            verify(service).get(ana.getIdUser(), YearMonth.of(2026, 8));
        }
    }

    @Nested
    @WebMvcTest(controllers = AuthController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ActiveProfiles("test")
    @DisplayName("AuthController")
    class Autenticacao {
        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AuthService authService;

        @Test
        @DisplayName("POST /auth/login devolve token e usuario")
        void loginDevolveToken() throws Exception {
            User ana = UserFixture.ana();
            when(authService.login("ana@email.com", "senha-segura")).thenReturn(
                new AuthService.LoginResult("token.assinatura",
                    new UserView(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null)));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ana@email.com\",\"password\":\"senha-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token.assinatura"))
                .andExpect(jsonPath("$.user.email").value("ana@email.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
        }

        @Test
        @DisplayName("POST /auth/login devolve 401 para credencial invalida")
        void credencialInvalidaDevolve401() throws Exception {
            when(authService.login(any(), any()))
                .thenThrow(new UnauthorizedException("Credenciais inválidas"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ana@email.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
        }

        @Test
        @DisplayName("POST /auth/login devolve 400 quando falta credencial")
        void credencialAusenteDevolve400() throws Exception {
            when(authService.login(any(), any()))
                .thenThrow(new IllegalArgumentException("E-mail e senha são obrigatórios"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /auth/login devolve 4xx para JSON malformado")
        void jsonMalformadoDevolve4xx() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{isso nao e json"))
                .andExpect(status().is4xxClientError());
        }
    }
}
