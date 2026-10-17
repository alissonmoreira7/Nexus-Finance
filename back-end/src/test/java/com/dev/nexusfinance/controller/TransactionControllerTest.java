package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.fixtures.FinanceFixture;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.models.TransactionSource;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.AccountService;
import com.dev.nexusfinance.services.AuthService;
import com.dev.nexusfinance.services.TransactionEngineService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice de {@link TransactionController} (QA-09).
 *
 * <p>Os filtros ficam desligados ({@code addFilters = false}) e a identidade do
 * requisitante e injetada como atributo de requisicao, do mesmo jeito que o
 * {@code AuthFilter} faz em producao. Assim este teste cobre o contrato HTTP; a
 * cadeia de seguranca tem testes proprios em {@code SecurityFilterChainTest}.
 */
@WebMvcTest(controllers = TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TransactionControllerTest {
    private static final String ATRIBUTO_USUARIO = "authenticatedUserId";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionEngineService service;

    @MockitoBean
    private AccountService accounts;

    /** Exigido pelo AuthFilter, que e um bean de filtro do slice web. */
    @MockitoBean
    private AuthService authService;

    private User ana;
    private Account conta;

    @BeforeEach
    void setUp() {
        ana = UserFixture.ana();
        conta = FinanceFixture.conta(ana);
    }

    @Test
    @DisplayName("POST /upload devolve 201 e a quantidade inserida")
    void uploadDevolve201ComContagem() throws Exception {
        when(service.processLote(eq(conta.getIdAccount()), any())).thenReturn(42);

        mockMvc.perform(post("/api/v1/transactions/upload")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("accountId", conta.getIdAccount().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(entradaJson()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("Lote processado com sucesso"))
            .andExpect(jsonPath("$.inserted").value(42));

        verify(accounts).assertOwnership(conta.getIdAccount(), ana.getIdUser());
    }

    @Test
    @DisplayName("POST /upload verifica o dono antes de processar")
    void uploadVerificaDono() throws Exception {
        doThrow(new UnauthorizedException("Acesso negado a esta conta"))
            .when(accounts).assertOwnership(conta.getIdAccount(), ana.getIdUser());

        mockMvc.perform(post("/api/v1/transactions/upload")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("accountId", conta.getIdAccount().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(entradaJson()))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Acesso negado a esta conta"));

        verify(service, never()).processLote(any(), any());
    }

    @Test
    @DisplayName("POST /manual devolve 201 com a transacao criada")
    void manualDevolve201() throws Exception {
        Transaction criada = FinanceFixture.transacao(
            conta, FinanceFixture.dicionarioPadrao().get(1), "89.90", LocalDate.of(2026, 8, 10));
        when(service.processManual(eq(conta.getIdAccount()), any())).thenReturn(criada);

        mockMvc.perform(post("/api/v1/transactions/manual")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("accountId", conta.getIdAccount().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entradaJson())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cleanDescription").value("IFOOD"))
            .andExpect(jsonPath("$.category").value("Alimentacao"))
            .andExpect(jsonPath("$.type").value("EXPENSE"))
            .andExpect(jsonPath("$.source").value("CSV"))
            .andExpect(jsonPath("$.amount").value(89.90));
    }

    @Test
    @DisplayName("POST /manual propaga 400 quando o tipo nao vem")
    void manualSemTipoDevolve400() throws Exception {
        when(service.processManual(any(), any()))
            .thenThrow(new IllegalArgumentException("Informe se a transação é uma entrada ou saída"));

        mockMvc.perform(post("/api/v1/transactions/manual")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("accountId", conta.getIdAccount().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entradaJson())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Informe se a transação é uma entrada ou saída"));
    }

    @Test
    @DisplayName("GET /{id} devolve o detalhe e verifica o dono da conta da transacao")
    void detalheVerificaDono() throws Exception {
        Transaction transacao = FinanceFixture.transacao(
            conta, FinanceFixture.dicionarioPadrao().get(1), "89.90", LocalDate.of(2026, 8, 10));
        when(service.findById(transacao.getIdTransaction())).thenReturn(transacao);

        mockMvc.perform(get("/api/v1/transactions/{id}", transacao.getIdTransaction())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountName").value("Banco Teste"))
            .andExpect(jsonPath("$.cleanDescription").value("IFOOD"));

        verify(accounts).assertOwnership(conta.getIdAccount(), ana.getIdUser());
    }

    @Test
    @DisplayName("GET /{id} devolve 404 para transacao inexistente")
    void detalheInexistenteDevolve404() throws Exception {
        UUID inexistente = UUID.randomUUID();
        when(service.findById(inexistente)).thenThrow(new ResourceNotFoundException("Transação não encontrada"));

        mockMvc.perform(get("/api/v1/transactions/{id}", inexistente)
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Transação não encontrada"));
    }

    @Test
    @DisplayName("GET /{id} devolve 400 para UUID malformado")
    void detalheComUuidMalformadoDevolve400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", "nao-e-uuid")
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /account/{id} devolve a pagina com os metadados de paginacao")
    void extratoDevolvePaginaComMetadados() throws Exception {
        Transaction transacao = FinanceFixture.transacao(
            conta, FinanceFixture.dicionarioPadrao().get(1), "89.90", LocalDate.of(2026, 8, 10));
        when(service.findByAccount(eq(conta.getIdAccount()), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(transacao), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/transactions/account/{id}", conta.getIdAccount())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @ParameterizedTest(name = "page={0} size={1} rejeitado")
    @CsvSource({"-1, 20", "0, 0", "0, -5", "0, 101", "-3, 500"})
    @DisplayName("GET /account/{id} rejeita paginacao fora do intervalo permitido")
    void extratoRejeitaPaginacaoInvalida(int page, int size) throws Exception {
        mockMvc.perform(get("/api/v1/transactions/account/{id}", conta.getIdAccount())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("Paginação inválida; size deve estar entre 1 e 100"));

        verify(service, never()).findByAccount(any(), any(), any(), any(), any());
    }

    @ParameterizedTest(name = "page={0} size={1} aceito")
    @CsvSource({"0, 1", "0, 100", "5, 20"})
    @DisplayName("GET /account/{id} aceita paginacao nas bordas do intervalo")
    void extratoAceitaPaginacaoNasBordas(int page, int size) throws Exception {
        when(service.findByAccount(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));

        mockMvc.perform(get("/api/v1/transactions/account/{id}", conta.getIdAccount())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /account/{id} rejeita busca acima de 100 caracteres")
    void extratoRejeitaBuscaLonga() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/account/{id}", conta.getIdAccount())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("search", "a".repeat(101)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("A busca deve ter no máximo 100 caracteres"));
    }

    @Test
    @DisplayName("GET /account/{id} repassa os filtros de origem, tipo e busca")
    void extratoRepassaFiltros() throws Exception {
        when(service.findByAccount(any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/transactions/account/{id}", conta.getIdAccount())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("source", "MANUAL")
                .param("type", "INCOME")
                .param("search", "ifood"))
            .andExpect(status().isOk());

        verify(service).findByAccount(
            eq(conta.getIdAccount()), eq(TransactionSource.MANUAL), eq(CategoryType.INCOME),
            eq("ifood"), any());
    }

    @Test
    @DisplayName("GET /account/{id} rejeita valor invalido de enum na query")
    void extratoRejeitaEnumInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/account/{id}", conta.getIdAccount())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser())
                .param("type", "NAO_EXISTE"))
            .andExpect(status().isBadRequest());
    }

    /**
     * S-01: este endpoint nao recebia o usuario autenticado nem verificava dono.
     * Qualquer usuario com token valido apagava a transacao de qualquer outro,
     * sem soft delete nem auditoria — perda definitiva e invisivel.
     */
    @Test
    @DisplayName("S-01: DELETE /{id} verifica o dono antes de apagar")
    void deleteVerificaDono() throws Exception {
        Transaction transacao = FinanceFixture.transacao(
            conta, FinanceFixture.dicionarioPadrao().get(1), "89.90", LocalDate.of(2026, 8, 10));
        when(service.findById(transacao.getIdTransaction())).thenReturn(transacao);

        mockMvc.perform(delete("/api/v1/transactions/{id}", transacao.getIdTransaction())
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isNoContent());

        verify(accounts).assertOwnership(conta.getIdAccount(), ana.getIdUser());
        verify(service).deleteTransaction(transacao.getIdTransaction());
    }

    @Test
    @DisplayName("S-01: DELETE /{id} de transacao alheia nao apaga nada")
    void deleteDeTransacaoAlheiaNaoApaga() throws Exception {
        User bruno = UserFixture.bruno();
        Transaction transacaoDaAna = FinanceFixture.transacao(
            conta, FinanceFixture.dicionarioPadrao().get(1), "89.90", LocalDate.of(2026, 8, 10));
        when(service.findById(transacaoDaAna.getIdTransaction())).thenReturn(transacaoDaAna);
        doThrow(new UnauthorizedException("Acesso negado a esta conta"))
            .when(accounts).assertOwnership(conta.getIdAccount(), bruno.getIdUser());

        mockMvc.perform(delete("/api/v1/transactions/{id}", transacaoDaAna.getIdTransaction())
                .requestAttr(ATRIBUTO_USUARIO, bruno.getIdUser()))
            .andExpect(status().isUnauthorized());

        verify(service, never()).deleteTransaction(any());
    }

    @Test
    @DisplayName("DELETE /{id} de transacao inexistente devolve 404")
    void deleteInexistenteDevolve404() throws Exception {
        UUID inexistente = UUID.randomUUID();
        when(service.findById(inexistente)).thenThrow(new ResourceNotFoundException("Transação não encontrada"));

        mockMvc.perform(delete("/api/v1/transactions/{id}", inexistente)
                .requestAttr(ATRIBUTO_USUARIO, ana.getIdUser()))
            .andExpect(status().isNotFound());

        verify(service, never()).deleteTransaction(any());
    }

    private TransactionEngineService.TransactionInput entradaJson() {
        return new TransactionEngineService.TransactionInput(
            LocalDate.of(2026, 8, 10), "COMPRA VISA*1234 IFOOD SAO PAULO",
            new BigDecimal("89.90"), CategoryType.EXPENSE);
    }
}
