package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserProfileImage;
import com.dev.nexusfinance.repositories.UserProfileImageRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private static final String SEGREDO = "test-secret-with-at-least-32-characters";
    private static final Instant AGORA = Instant.parse("2026-08-10T12:00:00Z");

    private UserRepository users;
    private UserProfileImageRepository profileImages;
    private AuthService auth;
    private User ana;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        profileImages = mock(UserProfileImageRepository.class);
        ana = UserFixture.ana();

        auth = authComRelogio(Clock.fixed(AGORA, ZoneOffset.UTC));

        when(users.findByEmailIgnoreCase("ana@email.com")).thenReturn(Optional.of(ana));
        when(users.findById(ana.getIdUser())).thenReturn(Optional.of(ana));
        when(profileImages.findById(ana.getIdUser())).thenReturn(Optional.empty());
    }

    private AuthService authComRelogio(Clock clock) {
        return new AuthService(users, profileImages, new PasswordService(), SEGREDO, clock);
    }

    @Test
    @DisplayName("autentica, gera token valido e aceita o token de volta")
    void autenticaEGeraTokenValido() {
        AuthService.LoginResult result = auth.login("  ana@email.com ", UserFixture.SENHA_PADRAO);
        AuthService.AuthenticatedUser authenticated = auth.validate(result.token());

        assertThat(result.user().id()).isEqualTo(ana.getIdUser());
        assertThat(result.user().email()).isEqualTo("ana@email.com");
        assertThat(authenticated.id()).isEqualTo(ana.getIdUser());
        assertThat(authenticated.details()).isSameAs(ana);
    }

    @Test
    @DisplayName("inclui a foto de perfil no resultado do login quando existe")
    void incluiFotoDePerfilNoLogin() {
        UserProfileImage foto = new UserProfileImage();
        foto.setUserId(ana.getIdUser());
        foto.setContentType("image/png");
        foto.setData(new byte[] {1, 2, 3});
        when(profileImages.findById(ana.getIdUser())).thenReturn(Optional.of(foto));

        AuthService.LoginResult result = auth.login("ana@email.com", UserFixture.SENHA_PADRAO);

        assertThat(result.user().profileImage()).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("rejeita senha incorreta sem revelar qual credencial falhou")
    void rejeitaSenhaIncorreta() {
        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.login("ana@email.com", "senha-errada"))
            .withMessage("Credenciais inválidas");
    }

    @Test
    @DisplayName("rejeita e-mail inexistente com a mesma mensagem da senha incorreta")
    void rejeitaEmailInexistenteComMesmaMensagem() {
        when(users.findByEmailIgnoreCase("ninguem@email.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.login("ninguem@email.com", UserFixture.SENHA_PADRAO))
            .withMessage("Credenciais inválidas");
    }

    @ParameterizedTest(name = "credencial ausente: email=[{0}] senha=[{1}]")
    @org.junit.jupiter.params.provider.CsvSource(nullValues = "NULL", value = {
        "NULL,            senha-segura",
        "'',              senha-segura",
        "'   ',           senha-segura",
        "ana@email.com,   NULL",
        "ana@email.com,   ''",
        "ana@email.com,   '   '"
    })
    @DisplayName("exige e-mail e senha preenchidos")
    void exigeCredenciaisPreenchidas(String email, String senha) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> auth.login(email, senha))
            .withMessage("E-mail e senha são obrigatórios");
    }

    @Test
    @DisplayName("rejeita token com assinatura adulterada")
    void rejeitaTokenAdulterado() {
        String token = auth.login("ana@email.com", UserFixture.SENHA_PADRAO).token();
        String adulterado = token.substring(0, token.length() - 1) + "x";

        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.validate(adulterado))
            .withMessage("Token inválido");
    }

    @Test
    @DisplayName("rejeita token assinado com outro segredo")
    void rejeitaTokenDeOutroSegredo() {
        AuthService outroServidor = new AuthService(
            users, profileImages, new PasswordService(),
            "um-outro-segredo-com-32-caracteres-ok", Clock.fixed(AGORA, ZoneOffset.UTC));
        String tokenEstrangeiro = outroServidor.login("ana@email.com", UserFixture.SENHA_PADRAO).token();

        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.validate(tokenEstrangeiro))
            .withMessage("Token inválido");
    }

    @ParameterizedTest(name = "token malformado: [{0}]")
    @ValueSource(strings = {"", "sem-ponto", "a.b.c", "....", "nao-base64!.assinatura"})
    @DisplayName("rejeita token com formato invalido")
    void rejeitaTokenMalformado(String malformado) {
        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.validate(malformado));
    }

    /**
     * Cenario que era inalcancavel antes da injecao de {@link Clock} (QA-13):
     * o TTL do token e de 8 horas, e sem controlar o relogio o unico jeito de
     * testar expiracao era esperar 8 horas.
     */
    @Test
    @DisplayName("rejeita token expirado — habilitado pela injecao de Clock")
    void rejeitaTokenExpirado() {
        String token = auth.login("ana@email.com", UserFixture.SENHA_PADRAO).token();

        AuthService oitoHorasDepois = authComRelogio(
            Clock.fixed(AGORA.plus(Duration.ofHours(8)).plusSeconds(1), ZoneOffset.UTC));

        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> oitoHorasDepois.validate(token))
            .withMessage("Token expirado");
    }

    @Test
    @DisplayName("aceita token um segundo antes de expirar")
    void aceitaTokenNaBordaDaValidade() {
        String token = auth.login("ana@email.com", UserFixture.SENHA_PADRAO).token();

        AuthService quaseExpirado = authComRelogio(
            Clock.fixed(AGORA.plus(Duration.ofHours(8)).minusSeconds(1), ZoneOffset.UTC));

        assertThat(quaseExpirado.validate(token).id()).isEqualTo(ana.getIdUser());
    }

    @Test
    @DisplayName("rejeita token de usuario que foi deletado depois do login")
    void rejeitaTokenDeUsuarioDeletado() {
        String token = auth.login("ana@email.com", UserFixture.SENHA_PADRAO).token();
        when(users.findById(ana.getIdUser())).thenReturn(Optional.empty());

        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.validate(token))
            .withMessage("Usuário da sessão não encontrado");
    }

    @Test
    @DisplayName("rejeita token cujo payload nao e um UUID")
    void rejeitaPayloadComIdInvalido() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("nao-e-uuid:" + (AGORA.getEpochSecond() + 3600)).getBytes(StandardCharsets.UTF_8));

        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> auth.validate(payload + ".assinatura-qualquer"))
            .withMessage("Token inválido");
    }

    @Test
    @DisplayName("loadUserByUsername devolve o usuario para o Spring Security")
    void carregaUsuarioPorEmail() {
        assertThat(auth.loadUserByUsername("ana@email.com")).isSameAs(ana);
    }

    @Test
    @DisplayName("loadUserByUsername lanca UsernameNotFoundException para e-mail desconhecido")
    void rejeitaEmailDesconhecidoNoUserDetailsService() {
        when(users.findByEmailIgnoreCase("ninguem@email.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(UsernameNotFoundException.class)
            .isThrownBy(() -> auth.loadUserByUsername("ninguem@email.com"))
            .withMessage("Usuário não encontrado");
    }

    @Test
    @DisplayName("gera tokens distintos em instantes distintos")
    void geraTokensDistintosNoTempo() {
        String primeiro = auth.login("ana@email.com", UserFixture.SENHA_PADRAO).token();
        String segundo = authComRelogio(Clock.fixed(AGORA.plusSeconds(60), ZoneOffset.UTC))
            .login("ana@email.com", UserFixture.SENHA_PADRAO).token();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    @DisplayName("token nao expoe o id do usuario em texto legivel")
    void tokenNaoExpoeIdEmTextoPuro() {
        String token = auth.login("ana@email.com", UserFixture.SENHA_PADRAO).token();

        assertThat(token).doesNotContain(ana.getIdUser().toString());
    }

    @Test
    @DisplayName("valida token de qualquer usuario existente")
    void validaTokenDeOutroUsuario() {
        User bruno = UserFixture.bruno();
        when(users.findByEmailIgnoreCase("bruno@email.com")).thenReturn(Optional.of(bruno));
        when(users.findById(bruno.getIdUser())).thenReturn(Optional.of(bruno));
        when(profileImages.findById(bruno.getIdUser())).thenReturn(Optional.empty());

        UUID id = auth.validate(auth.login("bruno@email.com", UserFixture.SENHA_PADRAO).token()).id();

        assertThat(id).isEqualTo(bruno.getIdUser());
    }
}
