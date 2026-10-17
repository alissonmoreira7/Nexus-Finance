package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.models.TransactionSource;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.TransactionRepository;
import com.dev.nexusfinance.services.AuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A matriz de autorizacao (QA-12) — o teste de melhor retorno do projeto.
 *
 * <p>Para <strong>cada</strong> endpoint que recebe um id na URL, verifica que o
 * usuario B nao alcanca o recurso do usuario A. Nao e um caso isolado: e uma
 * varredura, e cresce junto com a API.
 *
 * <p>Roda sobre o contexto completo com MySQL real: token emitido pelo
 * {@code AuthService} de verdade, atravessando {@code AuthFilter},
 * {@code SecurityConfig} e os controllers. E e o teste que pega o S-01 — antes
 * da correcao, a linha {@code EXCLUIR_TRANSACAO} respondia 204 e o dado da Ana
 * desaparecia para sempre.
 */
class AuthorizationMatrixIT extends AbstractIntegrationIT {

    /**
     * Os endpoints que recebem id de recurso na URL.
     *
     * <p>A URL e montada dentro do teste, nao num {@code @MethodSource}: o
     * provedor de argumentos e resolvido <em>antes</em> do {@code @BeforeEach},
     * quando os ids ainda nao existem.
     */
    enum EndpointComId {
        EXTRATO_DA_CONTA(HttpMethod.GET),
        DETALHE_DA_TRANSACAO(HttpMethod.GET),
        RESUMO_DO_ANALYTICS(HttpMethod.GET),
        CONTAS_DO_USUARIO(HttpMethod.GET),
        EXCLUIR_CONTA(HttpMethod.DELETE),
        EXCLUIR_TRANSACAO(HttpMethod.DELETE);

        final HttpMethod metodo;

        EndpointComId(HttpMethod metodo) {
            this.metodo = metodo;
        }
    }

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private TestDataFactory dados;

    @Autowired
    private AuthService auth;

    @Autowired
    private TransactionRepository transactions;

    private MockMvc mockMvc;
    private String tokenDaAna;
    private String tokenDoBruno;
    private User ana;
    private Account contaDaAna;
    private Transaction transacaoDaAna;

    @BeforeEach
    void prepararDoisUsuarios() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
            .apply(org.springframework.security.test.web.servlet.setup
                .SecurityMockMvcConfigurers.springSecurity())
            .build();

        dados.limparTudo();

        ana = dados.usuario("Ana Silva", "ana@email.com", "12345678901");
        User bruno = dados.usuario("Bruno Souza", "bruno@email.com", "98765432100");

        tokenDaAna = auth.login("ana@email.com", TestDataFactory.SENHA).token();
        tokenDoBruno = auth.login("bruno@email.com", TestDataFactory.SENHA).token();

        contaDaAna = dados.conta(ana, "Banco da Ana");
        dados.conta(bruno, "Banco do Bruno");

        List<Category> dicionario = dados.dicionario();
        transacaoDaAna = dados.transacao(contaDaAna, dicionario.get(1), "IFOOD",
            "89.90", LocalDate.now(), TransactionSource.CSV);
    }

    private String urlDe(EndpointComId endpoint) {
        return switch (endpoint) {
            case EXTRATO_DA_CONTA -> "/api/v1/transactions/account/" + contaDaAna.getIdAccount();
            case DETALHE_DA_TRANSACAO -> "/api/v1/transactions/" + transacaoDaAna.getIdTransaction();
            case RESUMO_DO_ANALYTICS -> "/api/v1/analytics/" + contaDaAna.getIdAccount() + "/summary";
            case CONTAS_DO_USUARIO -> "/api/v1/accounts/user/" + ana.getIdUser();
            case EXCLUIR_CONTA -> "/api/v1/accounts/" + contaDaAna.getIdAccount();
            case EXCLUIR_TRANSACAO -> "/api/v1/transactions/" + transacaoDaAna.getIdTransaction();
        };
    }

    @ParameterizedTest(name = "{0}: o token do Bruno nao alcanca o recurso da Ana")
    @EnumSource(EndpointComId.class)
    @DisplayName("nenhum endpoint com id na URL permite acesso cruzado entre usuarios")
    void usuarioNaoAlcancaRecursoDeOutro(EndpointComId endpoint) throws Exception {
        mockMvc.perform(request(endpoint.metodo, urlDe(endpoint))
                .header("Authorization", "Bearer " + tokenDoBruno))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}: sem token devolve 401")
    @EnumSource(EndpointComId.class)
    @DisplayName("nenhum endpoint com id na URL responde sem autenticacao")
    void semTokenNadaResponde(EndpointComId endpoint) throws Exception {
        mockMvc.perform(request(endpoint.metodo, urlDe(endpoint)))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}: a dona alcanca o proprio recurso")
    @EnumSource(value = EndpointComId.class, names = {
        "EXTRATO_DA_CONTA", "DETALHE_DA_TRANSACAO", "RESUMO_DO_ANALYTICS", "CONTAS_DO_USUARIO"})
    @DisplayName("a dona dos recursos continua alcancando todos eles")
    void donaAlcancaOsProprios(EndpointComId endpoint) throws Exception {
        mockMvc.perform(request(endpoint.metodo, urlDe(endpoint))
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest(name = "{0}: token invalido devolve 401")
    @EnumSource(EndpointComId.class)
    @DisplayName("token falsificado nao alcanca nenhum endpoint protegido")
    void tokenInvalidoNaoAlcancaNada(EndpointComId endpoint) throws Exception {
        mockMvc.perform(request(endpoint.metodo, urlDe(endpoint))
                .header("Authorization", "Bearer payload.falsificado"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * S-01, com a consequencia medida no banco. Antes da correcao, este endpoint
     * nao recebia o usuario autenticado e chamava {@code deleteById} direto. Nao
     * ha soft delete nem auditoria: a perda era definitiva e invisivel.
     */
    @Test
    @DisplayName("S-01: a transacao da Ana sobrevive a tentativa de exclusao do Bruno")
    void transacaoSobreviveATentativaDeExclusaoAlheia() throws Exception {
        mockMvc.perform(request(HttpMethod.DELETE, urlDe(EndpointComId.EXCLUIR_TRANSACAO))
                .header("Authorization", "Bearer " + tokenDoBruno))
            .andExpect(status().isUnauthorized());

        assertThat(transactions.findById(transacaoDaAna.getIdTransaction()))
            .as("a transacao da Ana nao pode ter sido apagada pelo Bruno")
            .isPresent();
    }

    @Test
    @DisplayName("a dona consegue excluir a propria transacao")
    void donaExcluiAPropriaTransacao() throws Exception {
        mockMvc.perform(request(HttpMethod.DELETE, urlDe(EndpointComId.EXCLUIR_TRANSACAO))
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().isNoContent());

        assertThat(transactions.findById(transacaoDaAna.getIdTransaction())).isEmpty();
    }

    @Test
    @DisplayName("a dona exclui a propria conta depois de limpar as transacoes")
    void donaExcluiAPropriaConta() throws Exception {
        mockMvc.perform(request(HttpMethod.DELETE, urlDe(EndpointComId.EXCLUIR_TRANSACAO))
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().isNoContent());

        mockMvc.perform(request(HttpMethod.DELETE, urlDe(EndpointComId.EXCLUIR_CONTA))
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().isNoContent());
    }

    /**
     * A FK de {@code tb_transactions} para {@code tb_accounts} e
     * {@code ON DELETE RESTRICT}: excluir conta com transacao e recusada pelo
     * banco. Antes do handler de {@code DataIntegrityViolationException} isso
     * respondia 500; agora responde 409.
     */
    @Test
    @DisplayName("excluir conta com transacoes devolve 409, nao 500")
    void excluirContaComTransacoesDevolve409() throws Exception {
        mockMvc.perform(request(HttpMethod.DELETE, urlDe(EndpointComId.EXCLUIR_CONTA))
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /users/me devolve o perfil do portador do token, nao de outro")
    void perfilSegueOPortadorDoToken() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/users/me")
                .header("Authorization", "Bearer " + tokenDoBruno))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("bruno@email.com"));

        mockMvc.perform(request(HttpMethod.GET, "/api/v1/users/me")
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("ana@email.com"));
    }

    @Test
    @DisplayName("o planejamento mensal e isolado por usuario")
    void planejamentoIsoladoPorUsuario() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT, "/api/v1/forecasts/2026-08")
                .header("Authorization", "Bearer " + tokenDaAna)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedIncome\":5000.00}"))
            .andExpect(status().isOk());

        mockMvc.perform(request(HttpMethod.GET, "/api/v1/forecasts/2026-08")
                .header("Authorization", "Bearer " + tokenDoBruno))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expectedIncome").value(0.00));
    }

    @Test
    @DisplayName("o gasto previsto da Ana nao e alcancavel pelo Bruno")
    void gastoPrevistoNaoEhAlcancavelPorOutro() throws Exception {
        String corpo = mockMvc.perform(request(HttpMethod.POST, "/api/v1/forecasts/2026-08/expenses")
                .header("Authorization", "Bearer " + tokenDaAna)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Aluguel\",\"amount\":1500.00}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String idDoGasto = JsonPath.read(corpo, "$.expenses[0].id");

        mockMvc.perform(request(HttpMethod.DELETE,
                "/api/v1/forecasts/2026-08/expenses/" + idDoGasto)
                .header("Authorization", "Bearer " + tokenDoBruno))
            .andExpect(status().isNotFound());

        mockMvc.perform(request(HttpMethod.GET, "/api/v1/forecasts/2026-08")
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(jsonPath("$.expenses[0].description").value("Aluguel"));
    }

    @Test
    @DisplayName("o extrato de uma conta nunca mistura transacoes de outra")
    void extratoNaoMisturaContas() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, urlDe(EndpointComId.EXTRATO_DA_CONTA))
                .header("Authorization", "Bearer " + tokenDaAna))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].cleanDescription").value("IFOOD"));
    }
}
