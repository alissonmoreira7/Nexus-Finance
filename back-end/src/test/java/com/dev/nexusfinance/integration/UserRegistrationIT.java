package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.models.PlanType;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserRole;
import com.dev.nexusfinance.repositories.UserRepository;
import com.dev.nexusfinance.services.AuthService;
import com.dev.nexusfinance.services.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de cadastro e login de ponta a ponta, contra o banco real.
 *
 * <p>O foco e o S-02: {@code SimpleJpaRepository.save()} escolhe entre
 * {@code persist} e {@code merge} pelo id ser nulo ou nao. Esse comportamento e
 * do Spring Data com um banco de verdade — nenhum mock o reproduz, e era
 * exatamente onde estava a vulnerabilidade de takeover de conta.
 */
class UserRegistrationIT extends AbstractIntegrationIT {

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private TestDataFactory dados;

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthService auth;

    @Autowired
    private PasswordService passwords;

    private MockMvc mockMvc;

    @BeforeEach
    void prepararContexto() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).apply(
            org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                .springSecurity()).build();
        dados.limparTudo();
    }

    @Test
    @DisplayName("cadastro seguido de login funciona de ponta a ponta")
    void cadastroELoginFuncionam() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana Silva","cpf":"123.456.789-01",
                     "email":"ANA@EMAIL.COM","password":"senha-segura"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("ana@email.com"))
            .andExpect(jsonPath("$.cpf").value("12345678901"));

        AuthService.LoginResult login = auth.login("ana@email.com", "senha-segura");

        assertThat(login.token()).isNotBlank();
        assertThat(login.user().name()).isEqualTo("Ana Silva");
    }

    @Test
    @DisplayName("a senha e persistida com hash PBKDF2, nunca em texto puro")
    void senhaEhPersistidaComHash() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana Silva","cpf":"12345678901",
                     "email":"ana@email.com","password":"senha-segura"}"""))
            .andExpect(status().isCreated());

        User gravada = users.findByEmailIgnoreCase("ana@email.com").orElseThrow();

        assertThat(gravada.getPassword())
            .isNotEqualTo("senha-segura")
            .startsWith("pbkdf2$210000$");
        assertThat(passwords.matches("senha-segura", gravada.getPassword())).isTrue();
    }

    /**
     * S-02 na sua forma original: o atacante envia {@code idUser} da vitima no
     * corpo do cadastro — endpoint publico. Com o id preenchido, {@code save()}
     * vira {@code merge}, ou seja um {@code UPDATE} na linha da vitima, trocando
     * e-mail e senha. As checagens de unicidade nao protegem, porque os valores
     * enviados sao novos.
     *
     * <p>A defesa e {@code user.setIdUser(null)} em {@code UserService.create}.
     */
    @Test
    @DisplayName("S-02: cadastro com idUser da vitima nao sobrescreve a conta dela")
    void cadastroNaoSobrescreveUsuarioExistente() throws Exception {
        User vitima = dados.usuario("Ana Silva", "ana@email.com", "12345678901");
        UUID idDaVitima = vitima.getIdUser();
        String hashOriginal = vitima.getPassword();

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idUser":"%s","name":"Invasor","cpf":"99988877766",
                     "email":"invasor@email.com","password":"senha-do-invasor"}
                    """.formatted(idDaVitima)))
            .andExpect(status().isCreated());

        User aindaEhAna = users.findById(idDaVitima).orElseThrow();

        assertThat(aindaEhAna.getEmail())
            .as("o e-mail da vitima nao pode ter sido trocado pelo do atacante")
            .isEqualTo("ana@email.com");
        assertThat(aindaEhAna.getName()).isEqualTo("Ana Silva");
        assertThat(aindaEhAna.getPassword())
            .as("a senha da vitima nao pode ter sido trocada")
            .isEqualTo(hashOriginal);
        assertThat(passwords.matches("senha-do-invasor", aindaEhAna.getPassword())).isFalse();
    }

    @Test
    @DisplayName("S-02: o cadastro do invasor cria uma linha nova, com id proprio")
    void cadastroDoInvasorCriaLinhaNova() throws Exception {
        User vitima = dados.usuario("Ana Silva", "ana@email.com", "12345678901");

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idUser":"%s","name":"Invasor","cpf":"99988877766",
                     "email":"invasor@email.com","password":"senha-do-invasor"}
                    """.formatted(vitima.getIdUser())))
            .andExpect(status().isCreated());

        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findByEmailIgnoreCase("invasor@email.com"))
            .get()
            .extracting(User::getIdUser)
            .isNotEqualTo(vitima.getIdUser());
    }

    @Test
    @DisplayName("papel e plano sao definidos pelo servidor, nao pelo cliente")
    void papelEPlanoSaoDefinidosPeloServidor() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana Silva","cpf":"12345678901","email":"ana@email.com",
                     "password":"senha-segura","plan":"PREMIUM","role":"USER"}"""))
            .andExpect(status().isCreated());

        User gravada = users.findByEmailIgnoreCase("ana@email.com").orElseThrow();

        assertThat(gravada.getPlan()).isEqualTo(PlanType.BASIC);
        assertThat(gravada.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("cadastro com e-mail ja existente devolve 400 e nao cria linha")
    void emailDuplicadoDevolve400() throws Exception {
        dados.usuario("Ana Silva", "ana@email.com", "12345678901");

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Outra","cpf":"99988877766",
                     "email":"ana@email.com","password":"senha-segura"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("E-mail já cadastrado"));

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("cadastro com CPF ja existente devolve 400")
    void cpfDuplicadoDevolve400() throws Exception {
        dados.usuario("Ana Silva", "ana@email.com", "12345678901");

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Outra","cpf":"123.456.789-01",
                     "email":"outra@email.com","password":"senha-segura"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("CPF já cadastrado"));
    }

    @Test
    @DisplayName("a resposta do cadastro nao carrega o hash da senha")
    void respostaNaoCarregaHash() throws Exception {
        String corpo = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ana Silva","cpf":"12345678901",
                     "email":"ana@email.com","password":"senha-segura"}"""))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertThat(corpo)
            .doesNotContain("pbkdf2")
            .doesNotContain("senha-segura")
            .doesNotContain("password");
    }

    @Test
    @DisplayName("o login devolve 401 para senha errada, sem revelar se o e-mail existe")
    void loginComSenhaErradaDevolve401() throws Exception {
        dados.usuario("Ana Silva", "ana@email.com", "12345678901");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@email.com\",\"password\":\"errada\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Credenciais inválidas"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ninguem@email.com\",\"password\":\"errada\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    @DisplayName("o login aceita e-mail com caixa e espacos diferentes")
    void loginNormalizaEmail() throws Exception {
        dados.usuario("Ana Silva", "ana@email.com", "12345678901");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  ANA@EMAIL.COM \",\"password\":\"senha-segura\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
