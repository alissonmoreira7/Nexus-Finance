package com.dev.nexusfinance.config;

import com.dev.nexusfinance.controller.AccountController;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.fixtures.FinanceFixture;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.services.AccountService;
import com.dev.nexusfinance.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes da cadeia de seguranca: {@link AuthFilter} e {@link SecurityConfig} (QA-10).
 *
 * <p>Diferente dos slices de controller, aqui os filtros ficam <strong>ligados</strong>:
 * o objetivo e exercitar a camada que decide quem entra. Eram 105 linhas com zero
 * cobertura — a parte menos testada do projeto, num sistema financeiro.
 */
@WebMvcTest(controllers = AccountController.class)
@Import({SecurityConfig.class, AuthFilter.class, WebConfig.class})
@ActiveProfiles("test")
class SecurityChainTest {
    private static final String TOKEN_VALIDO = "payload.assinatura";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AccountService accounts;

    private User ana;

    @BeforeEach
    void setUp() {
        ana = UserFixture.ana();
        when(accounts.findByUser(ana.getIdUser())).thenReturn(List.of(FinanceFixture.conta(ana)));
    }

    private void comTokenValido() {
        when(authService.validate(TOKEN_VALIDO))
            .thenReturn(new AuthService.AuthenticatedUser(ana.getIdUser(), ana));
    }

    @Test
    @DisplayName("rota protegida sem header Authorization devolve 401 em JSON")
    void semHeaderDevolve401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Autenticação obrigatória"));

        verify(authService, never()).validate(any());
    }

    @ParameterizedTest(name = "header [{0}] nao autentica")
    @ValueSource(strings = {
        "Basic YWRtaW46YWRtaW4=",
        "Bearer",
        "bearer token-minusculo",
        "Token abc",
        "BearerSemEspaco"
    })
    @DisplayName("header Authorization em formato nao suportado nao autentica")
    void headerMalformadoNaoAutentica(String header) throws Exception {
        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", header))
            .andExpect(status().isUnauthorized());

        verify(authService, never()).validate(any());
    }

    @Test
    @DisplayName("token valido autentica e injeta authenticatedUserId no request")
    void tokenValidoAutentica() throws Exception {
        comTokenValido();

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer " + TOKEN_VALIDO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        verify(authService).validate(TOKEN_VALIDO);
        verify(accounts).findByUser(ana.getIdUser());
    }

    @Test
    @DisplayName("token adulterado devolve 401 com a mensagem do dominio")
    void tokenAdulteradoDevolve401() throws Exception {
        when(authService.validate("token-adulterado"))
            .thenThrow(new UnauthorizedException("Token inválido"));

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer token-adulterado"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Token inválido"));
    }

    @Test
    @DisplayName("token expirado devolve 401")
    void tokenExpiradoDevolve401() throws Exception {
        when(authService.validate("token-expirado"))
            .thenThrow(new UnauthorizedException("Token expirado"));

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer token-expirado"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Token expirado"));
    }

    @Test
    @DisplayName("token de usuario deletado devolve 401")
    void tokenDeUsuarioDeletadoDevolve401() throws Exception {
        when(authService.validate(TOKEN_VALIDO))
            .thenThrow(new UnauthorizedException("Usuário da sessão não encontrado"));

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer " + TOKEN_VALIDO))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Usuário da sessão não encontrado"));
    }

    /**
     * O filtro monta o JSON de erro na mao. Uma mensagem com aspas ou barra
     * invertida precisa ser escapada, senao o corpo da resposta deixa de ser JSON
     * valido — e o cliente recebe um erro de parse em vez de um 401.
     */
    @Test
    @DisplayName("mensagem com aspas e barras e escapada no JSON do 401")
    void escapaMensagemComCaracteresEspeciais() throws Exception {
        when(authService.validate("token-estranho"))
            .thenThrow(new UnauthorizedException("Token \"invalido\" com \\barra"));

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer token-estranho"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Token \"invalido\" com \\barra"));
    }

    @Test
    @DisplayName("o 401 do filtro vem em UTF-8, com acento legivel")
    void respostaDoFiltroVemEmUtf8() throws Exception {
        when(authService.validate("token-x"))
            .thenThrow(new UnauthorizedException("Autenticação inválida"));

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer token-x"))
            .andExpect(jsonPath("$.message").value("Autenticação inválida"));
    }

    @Test
    @DisplayName("OPTIONS e liberado sem token, para o preflight de CORS")
    void preflightEhLiberado() throws Exception {
        mockMvc.perform(options("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk());

        verify(authService, never()).validate(any());
    }

    @Test
    @DisplayName("POST /auth/login e publico")
    void loginEhPublico() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@email.com\",\"password\":\"senha-segura\"}"))
            .andExpect(status().isNotFound());

        verify(authService, never()).validate(any());
    }

    @Test
    @DisplayName("POST /users e publico, para permitir o cadastro")
    void cadastroEhPublico() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana\"}"))
            .andExpect(status().isNotFound());

        verify(authService, never()).validate(any());
    }

    @Test
    @DisplayName("GET /users nao e publico — apenas o POST de cadastro e")
    void somenteOPostDeUsuariosEhPublico() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o token nao cria sessao: a politica e stateless")
    void naoCriaSessao() throws Exception {
        comTokenValido();

        mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                .header("Authorization", "Bearer " + TOKEN_VALIDO))
            .andExpect(status().isOk())
            .andExpect(result -> {
                var sessao = result.getRequest().getSession(false);
                org.assertj.core.api.Assertions.assertThat(sessao == null
                        || sessao.getAttribute("SPRING_SECURITY_CONTEXT") == null)
                    .as("nenhum SecurityContext deve ser persistido em sessao")
                    .isTrue();
            });
    }

    @Test
    @DisplayName("cada requisicao revalida o token, sem reaproveitar autenticacao")
    void revalidaTokenEmCadaRequisicao() throws Exception {
        comTokenValido();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/accounts/user/{id}", ana.getIdUser())
                    .header("Authorization", "Bearer " + TOKEN_VALIDO))
                .andExpect(status().isOk());
        }

        verify(authService, org.mockito.Mockito.times(3)).validate(TOKEN_VALIDO);
    }
}
