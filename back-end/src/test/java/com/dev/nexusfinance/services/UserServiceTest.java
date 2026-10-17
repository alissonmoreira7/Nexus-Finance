package com.dev.nexusfinance.services;

import com.dev.nexusfinance.dto.UserView;
import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.PlanType;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserProfileImage;
import com.dev.nexusfinance.models.UserRole;
import com.dev.nexusfinance.repositories.UserProfileImageRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
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

class UserServiceTest {
    /** JPEG, PNG e WebP minimos validos — so os magic bytes que o servico inspeciona. */
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};
    private static final byte[] WEBP = {
        0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};

    private UserRepository users;
    private UserProfileImageRepository images;
    private UserService service;
    private User ana;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        images = mock(UserProfileImageRepository.class);
        service = new UserService(users, images, new PasswordService());

        ana = UserFixture.ana();
        when(users.findById(ana.getIdUser())).thenReturn(Optional.of(ana));
        when(images.findById(ana.getIdUser())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(images.save(any(UserProfileImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("cadastro")
    class Cadastro {

        @Test
        @DisplayName("normaliza dados, hasheia a senha e aplica papel e plano padrao")
        void cadastraComDadosNormalizados() {
            User novo = UserFixture.paraCadastro(
                "  Ana Silva  ", "ANA@EMAIL.COM", "123.456.789-01", "senha-crua");

            User salvo = service.create(novo);

            assertThat(salvo.getName()).isEqualTo("Ana Silva");
            assertThat(salvo.getEmail()).isEqualTo("ana@email.com");
            assertThat(salvo.getCpf()).isEqualTo("12345678901");
            assertThat(salvo.getRole()).isEqualTo(UserRole.USER);
            assertThat(salvo.getPlan()).isEqualTo(PlanType.BASIC);
            assertThat(salvo.getPassword()).isNotEqualTo("senha-crua").startsWith("pbkdf2$");
        }

        /**
         * QA-22: {@code create} valida o e-mail <em>antes</em> de trimar, enquanto
         * {@code updateProfile} trima <em>antes</em> de validar. Consequencia: um
         * e-mail com espaco nas pontas e recusado no cadastro e aceito na edicao de
         * perfil — duas regras diferentes para o mesmo campo.
         *
         * <p>Encontrado ao escrever este teste. Fixa a divergencia para que a
         * unificacao seja uma mudanca deliberada e visivel no diff.
         */
        @Test
        @DisplayName("QA-22: cadastro recusa e-mail com espacos nas pontas (divergente de updateProfile)")
        void recusaEmailComEspacosNoCadastro() {
            User comEspacos = UserFixture.paraCadastro(
                "Ana Silva", "  ana@email.com  ", "12345678901", "senha-crua");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(comEspacos))
                .withMessage("E-mail inválido");

            // O mesmo valor passa pela edicao de perfil:
            assertThat(service.updateProfile(ana.getIdUser(), "Ana Silva", "  ana@email.com  ",
                "12345678901", null, null, false).email())
                .isEqualTo("ana@email.com");
        }

        /**
         * QA-23: {@code create} apenas apara as pontas do nome, enquanto
         * {@code updateProfile} tambem colapsa espacos internos e aplica limite de
         * 100 caracteres. Mesma assimetria do QA-22, no campo nome.
         */
        @Test
        @DisplayName("QA-23: cadastro preserva espacos internos do nome (divergente de updateProfile)")
        void preservaEspacosInternosNoCadastro() {
            User novo = UserFixture.paraCadastro(
                "  Ana   Maria  ", "ana@email.com", "12345678901", "senha-crua");

            assertThat(service.create(novo).getName()).isEqualTo("Ana   Maria");
        }

        @Test
        @DisplayName("QA-23: cadastro nao aplica o limite de 100 caracteres do nome")
        void naoAplicaLimiteDeNomeNoCadastro() {
            User novo = UserFixture.paraCadastro(
                "A".repeat(150), "ana@email.com", "12345678901", "senha-crua");

            assertThat(service.create(novo).getName()).hasSize(150);
        }

        /**
         * S-02: o endpoint de cadastro e publico e recebe a entidade JPA crua. Com
         * {@code idUser} preenchido, {@code save()} faz merge em vez de persist — um
         * UPDATE na linha daquele UUID, trocando e-mail e senha da vitima. Takeover
         * de conta sem autenticacao.
         */
        @Test
        @DisplayName("S-02: descarta idUser enviado pelo cliente para impedir takeover por merge")
        void descartaIdEnviadoPeloCliente() {
            UUID idDaVitima = UUID.randomUUID();
            User malicioso = UserFixture.paraCadastro(
                "Invasor", "invasor@email.com", "11122233344", "senha-crua");
            malicioso.setIdUser(idDaVitima);

            service.create(malicioso);

            ArgumentCaptor<User> salvo = ArgumentCaptor.captor();
            verify(users).save(salvo.capture());
            assertThat(salvo.getValue().getIdUser())
                .as("id vindo do cliente deve ser descartado, senao save() vira UPDATE")
                .isNull();
        }

        @Test
        @DisplayName("ignora papel e plano enviados pelo cliente")
        void ignoraPapelEPlanoDoCliente() {
            User malicioso = UserFixture.paraCadastro(
                "Invasor", "invasor@email.com", "11122233344", "senha-crua");
            malicioso.setPlan(PlanType.PREMIUM);

            assertThat(service.create(malicioso).getPlan()).isEqualTo(PlanType.BASIC);
        }

        @Test
        @DisplayName("rejeita CPF ja cadastrado")
        void rejeitaCpfDuplicado() {
            when(users.findByCpf("12345678901")).thenReturn(Optional.of(ana));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(UserFixture.paraCadastro(
                    "Outra", "outra@email.com", "12345678901", "senha-crua")))
                .withMessage("CPF já cadastrado");
            verify(users, never()).save(any());
        }

        @Test
        @DisplayName("rejeita e-mail ja cadastrado")
        void rejeitaEmailDuplicado() {
            when(users.existsByEmailIgnoreCase("ana@email.com")).thenReturn(true);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(UserFixture.paraCadastro(
                    "Outra", "ana@email.com", "99988877766", "senha-crua")))
                .withMessage("E-mail já cadastrado");
            verify(users, never()).save(any());
        }

        @Test
        @DisplayName("rejeita usuario nulo")
        void rejeitaUsuarioNulo() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(null))
                .withMessage("Nome é obrigatório");
        }

        @ParameterizedTest(name = "{0} -> \"{4}\"")
        @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "nome nulo          | NULL | a@b.com      | 12345678901 | Nome é obrigatório",
            "nome vazio         | ''   | a@b.com      | 12345678901 | Nome é obrigatório",
            "nome em branco     | '  ' | a@b.com      | 12345678901 | Nome é obrigatório",
            "cpf nulo           | Ana  | a@b.com      | NULL        | CPF deve conter 11 dígitos",
            "cpf curto          | Ana  | a@b.com      | 123456789   | CPF deve conter 11 dígitos",
            "cpf longo          | Ana  | a@b.com      | 123456789012| CPF deve conter 11 dígitos",
            "cpf com letras     | Ana  | a@b.com      | abcdefghijk | CPF deve conter 11 dígitos",
            "email nulo         | Ana  | NULL         | 12345678901 | E-mail inválido",
            "email sem arroba   | Ana  | anaemail.com | 12345678901 | E-mail inválido",
            "email sem dominio  | Ana  | ana@         | 12345678901 | E-mail inválido",
            "email sem tld      | Ana  | ana@email    | 12345678901 | E-mail inválido",
            "email com espaco   | Ana  | 'a na@b.com' | 12345678901 | E-mail inválido"
        })
        @DisplayName("rejeita dados invalidos no cadastro")
        void rejeitaDadosInvalidos(String caso, String nome, String email, String cpf, String mensagem) {
            User invalido = UserFixture.paraCadastro(nome, email, cpf, "senha-crua");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(invalido))
                .withMessage(mensagem);
        }

        @ParameterizedTest(name = "senha de {0} caracteres")
        @ValueSource(ints = {0, 1, 5})
        @DisplayName("exige senha com no minimo 6 caracteres")
        void exigeSenhaMinima(int tamanho) {
            User invalido = UserFixture.paraCadastro(
                "Ana", "ana@email.com", "12345678901", "x".repeat(tamanho));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(invalido))
                .withMessage("Senha deve ter no mínimo 6 caracteres");
        }

        @Test
        @DisplayName("rejeita senha nula")
        void rejeitaSenhaNula() {
            User invalido = UserFixture.paraCadastro("Ana", "ana@email.com", "12345678901", null);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(invalido))
                .withMessage("Senha deve ter no mínimo 6 caracteres");
        }

        @Test
        @DisplayName("aceita senha de exatamente 6 caracteres")
        void aceitaSenhaNoLimite() {
            User valido = UserFixture.paraCadastro("Ana", "ana@email.com", "12345678901", "123456");

            assertThat(service.create(valido).getPassword()).startsWith("pbkdf2$");
        }
    }

    @Nested
    @DisplayName("consulta")
    class Consulta {

        @Test
        @DisplayName("encontra usuario por id")
        void encontraPorId() {
            assertThat(service.findById(ana.getIdUser())).isSameAs(ana);
        }

        @Test
        @DisplayName("lanca not found para id inexistente")
        void rejeitaIdInexistente() {
            UUID inexistente = UUID.randomUUID();
            when(users.findById(inexistente)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.findById(inexistente))
                .withMessage("Usuário não encontrado");
        }

        @Test
        @DisplayName("encontra por CPF ignorando mascara")
        void encontraPorCpfComMascara() {
            when(users.findByCpf("12345678901")).thenReturn(Optional.of(ana));

            assertThat(service.findByCpf("123.456.789-01")).isSameAs(ana);
        }

        @Test
        @DisplayName("lanca not found para CPF inexistente")
        void rejeitaCpfInexistente() {
            when(users.findByCpf("99999999999")).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.findByCpf("999.999.999-99"))
                .withMessage("Usuário não encontrado");
        }

        @Test
        @DisplayName("lista todos os usuarios")
        void listaTodos() {
            when(users.findAll()).thenReturn(List.of(ana));

            assertThat(service.findAll()).containsExactly(ana);
        }

        @Test
        @DisplayName("monta o perfil sem foto quando nao existe imagem")
        void montaPerfilSemFoto() {
            UserView perfil = service.getProfile(ana.getIdUser());

            assertThat(perfil.name()).isEqualTo("Ana Silva");
            assertThat(perfil.profileImage()).isNull();
        }

        @Test
        @DisplayName("monta o perfil com a foto em data URI")
        void montaPerfilComFoto() {
            UserProfileImage foto = new UserProfileImage();
            foto.setUserId(ana.getIdUser());
            foto.setContentType("image/png");
            foto.setData(PNG);
            when(images.findById(ana.getIdUser())).thenReturn(Optional.of(foto));

            assertThat(service.getProfile(ana.getIdUser()).profileImage())
                .startsWith("data:image/png;base64,");
        }
    }

    @Nested
    @DisplayName("exclusao")
    class Exclusao {

        @Test
        @DisplayName("exclui usuario existente")
        void excluiExistente() {
            when(users.existsById(ana.getIdUser())).thenReturn(true);

            service.delete(ana.getIdUser());

            verify(users).deleteById(ana.getIdUser());
        }

        @Test
        @DisplayName("lanca not found antes de tentar excluir inexistente")
        void rejeitaExclusaoDeInexistente() {
            UUID inexistente = UUID.randomUUID();
            when(users.existsById(inexistente)).thenReturn(false);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.delete(inexistente))
                .withMessage("Usuário não encontrado");
            verify(users, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("atualizacao de perfil")
    class AtualizacaoDePerfil {

        @Test
        @DisplayName("normaliza nome, e-mail e CPF e armazena foto valida")
        void atualizaDadosEArmazenaFoto() {
            UserView result = service.updateProfile(
                ana.getIdUser(), "  Ana   Silva  ", " ANA@EMAIL.COM ", "987.654.321-00",
                JPEG, "image/jpeg", false);

            assertThat(result.name()).isEqualTo("Ana Silva");
            assertThat(result.email()).isEqualTo("ana@email.com");
            assertThat(result.cpf()).isEqualTo("98765432100");

            ArgumentCaptor<UserProfileImage> imagem = ArgumentCaptor.captor();
            verify(images).save(imagem.capture());
            assertThat(imagem.getValue().getContentType()).isEqualTo("image/jpeg");
            assertThat(imagem.getValue().getUserId()).isEqualTo(ana.getIdUser());
        }

        @ParameterizedTest(name = "{0} e aceito")
        @CsvSource(delimiter = '|', value = {
            "image/jpeg | JPEG",
            "image/png  | PNG",
            "image/webp | WEBP"
        })
        @DisplayName("aceita os tres formatos de imagem suportados")
        void aceitaFormatosSuportados(String contentType, String formato) {
            byte[] dados = switch (formato) {
                case "JPEG" -> JPEG;
                case "PNG" -> PNG;
                default -> WEBP;
            };

            service.updateProfile(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                dados, contentType, false);

            verify(images).save(any(UserProfileImage.class));
        }

        @Test
        @DisplayName("rejeita arquivo disfarcado de imagem pelos magic bytes")
        void rejeitaArquivoDisfarcado() {
            byte[] executavel = "arquivo executável".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                    executavel, "image/jpeg", false))
                .withMessage("Envie uma foto JPEG, PNG ou WebP válida");
            verify(images, never()).save(any());
        }

        @ParameterizedTest(name = "content-type {0} e recusado")
        @ValueSource(strings = {"image/gif", "application/pdf", "text/plain", "image/svg+xml", ""})
        @DisplayName("recusa content-type nao suportado mesmo com bytes de imagem")
        void recusaContentTypeNaoSuportado(String contentType) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                    JPEG, contentType, false))
                .withMessage("Envie uma foto JPEG, PNG ou WebP válida");
        }

        @Test
        @DisplayName("recusa content-type nulo")
        void recusaContentTypeNulo() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                    JPEG, null, false))
                .withMessage("Envie uma foto JPEG, PNG ou WebP válida");
        }

        @Test
        @DisplayName("recusa WebP cujo cabecalho RIFF nao declara WEBP")
        void recusaRiffQueNaoEhWebp() {
            byte[] riffFalso = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x56, 0x49, 0x20};

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                    riffFalso, "image/webp", false))
                .withMessage("Envie uma foto JPEG, PNG ou WebP válida");
        }

        @Test
        @DisplayName("recusa imagem truncada menor que a assinatura")
        void recusaImagemTruncada() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                    new byte[] {(byte) 0xff}, "image/jpeg", false))
                .withMessage("Envie uma foto JPEG, PNG ou WebP válida");
        }

        @Test
        @DisplayName("recusa foto acima de 1 MB")
        void recusaFotoAcimaDoLimite() {
            byte[] gigante = new byte[1_000_001];
            gigante[0] = (byte) 0xff;
            gigante[1] = (byte) 0xd8;
            gigante[2] = (byte) 0xff;

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                    gigante, "image/jpeg", false))
                .withMessage("A foto deve ter no máximo 1 MB");
        }

        @Test
        @DisplayName("remove a foto quando removeImage e verdadeiro")
        void removeFoto() {
            when(images.existsById(ana.getIdUser())).thenReturn(true);

            UserView result = service.updateProfile(
                ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null, null, true);

            verify(images).deleteById(ana.getIdUser());
            assertThat(result.profileImage()).isNull();
        }

        @Test
        @DisplayName("removeImage e idempotente quando nao havia foto")
        void removeFotoInexistenteSemErro() {
            when(images.existsById(ana.getIdUser())).thenReturn(false);

            service.updateProfile(
                ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null, null, true);

            verify(images, never()).deleteById(any());
        }

        @Test
        @DisplayName("removeImage tem precedencia sobre a foto enviada")
        void removeImagePrecedeUpload() {
            when(images.existsById(ana.getIdUser())).thenReturn(true);

            service.updateProfile(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                JPEG, "image/jpeg", true);

            verify(images).deleteById(ana.getIdUser());
            verify(images, never()).save(any());
        }

        @Test
        @DisplayName("preserva a foto existente quando nenhuma nova e enviada")
        void preservaFotoExistente() {
            UserProfileImage existente = new UserProfileImage();
            existente.setUserId(ana.getIdUser());
            existente.setContentType("image/png");
            existente.setData(PNG);
            when(images.findById(ana.getIdUser())).thenReturn(Optional.of(existente));

            UserView result = service.updateProfile(
                ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null, null, false);

            assertThat(result.profileImage()).startsWith("data:image/png;base64,");
            verify(images, never()).save(any());
        }

        @Test
        @DisplayName("trata array de bytes vazio como ausencia de foto")
        void trataArrayVazioComoAusencia() {
            service.updateProfile(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                new byte[0], "image/jpeg", false);

            verify(images, never()).save(any());
        }

        @Test
        @DisplayName("substitui a foto existente reaproveitando a mesma linha")
        void substituiFotoExistente() {
            UserProfileImage existente = new UserProfileImage();
            existente.setUserId(ana.getIdUser());
            existente.setContentType("image/png");
            existente.setData(PNG);
            when(images.findById(ana.getIdUser())).thenReturn(Optional.of(existente));

            service.updateProfile(ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901",
                JPEG, "image/jpeg", false);

            ArgumentCaptor<UserProfileImage> salva = ArgumentCaptor.captor();
            verify(images).save(salva.capture());
            assertThat(salva.getValue()).isSameAs(existente);
            assertThat(salva.getValue().getContentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("rejeita e-mail ja usado por outro usuario")
        void rejeitaEmailDeOutroUsuario() {
            when(users.existsByEmailIgnoreCaseAndIdUserNot("ana@email.com", ana.getIdUser()))
                .thenReturn(true);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null, null, false))
                .withMessage("E-mail já cadastrado");
        }

        @Test
        @DisplayName("rejeita CPF ja usado por outro usuario")
        void rejeitaCpfDeOutroUsuario() {
            when(users.existsByCpfAndIdUserNot("12345678901", ana.getIdUser())).thenReturn(true);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", "12345678901", null, null, false))
                .withMessage("CPF já cadastrado");
        }

        @ParameterizedTest(name = "{0} -> \"{2}\"")
        @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "nome nulo     | NULL | Nome é obrigatório",
            "nome vazio    | ''   | Nome é obrigatório",
            "nome em branco| '  ' | Nome é obrigatório"
        })
        @DisplayName("rejeita nome ausente na atualizacao")
        void rejeitaNomeAusente(String caso, String nome, String mensagem) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), nome, "ana@email.com", "12345678901", null, null, false))
                .withMessage(mensagem);
        }

        @Test
        @DisplayName("rejeita nome acima de 100 caracteres")
        void rejeitaNomeLongo() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "A".repeat(101), "ana@email.com", "12345678901",
                    null, null, false))
                .withMessage("Nome deve ter no máximo 100 caracteres");
        }

        @Test
        @DisplayName("aceita nome de exatamente 100 caracteres")
        void aceitaNomeNoLimite() {
            UserView result = service.updateProfile(
                ana.getIdUser(), "A".repeat(100), "ana@email.com", "12345678901",
                null, null, false);

            assertThat(result.name()).hasSize(100);
        }

        @Test
        @DisplayName("colapsa espacos internos multiplos no nome")
        void colapsaEspacosInternos() {
            UserView result = service.updateProfile(
                ana.getIdUser(), "Ana     Maria      Silva", "ana@email.com", "12345678901",
                null, null, false);

            assertThat(result.name()).isEqualTo("Ana Maria Silva");
        }

        @Test
        @DisplayName("rejeita e-mail acima de 100 caracteres")
        void rejeitaEmailLongo() {
            String emailLongo = "a".repeat(95) + "@b.com";

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", emailLongo, "12345678901", null, null, false))
                .withMessage("E-mail deve ter no máximo 100 caracteres");
        }

        @ParameterizedTest(name = "e-mail invalido: [{0}]")
        @CsvSource(nullValues = "NULL", value = {"NULL", "''", "anaemail.com", "ana@", "ana@email", "'a na@b.com'"})
        @DisplayName("rejeita e-mail invalido na atualizacao")
        void rejeitaEmailInvalido(String email) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", email, "12345678901", null, null, false))
                .withMessage("E-mail inválido");
        }

        @ParameterizedTest(name = "CPF invalido: [{0}]")
        @CsvSource(nullValues = "NULL", value = {"NULL", "''", "123456789", "123456789012", "abcdefghijk"})
        @DisplayName("rejeita CPF invalido na atualizacao")
        void rejeitaCpfInvalido(String cpf) {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateProfile(
                    ana.getIdUser(), "Ana Silva", "ana@email.com", cpf, null, null, false))
                .withMessage("CPF deve conter 11 dígitos");
        }

        @Test
        @DisplayName("lanca not found ao atualizar usuario inexistente")
        void rejeitaUsuarioInexistente() {
            UUID inexistente = UUID.randomUUID();
            when(users.findById(inexistente)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.updateProfile(
                    inexistente, "Ana Silva", "ana@email.com", "12345678901", null, null, false))
                .withMessage("Usuário não encontrado");
        }
    }
}
