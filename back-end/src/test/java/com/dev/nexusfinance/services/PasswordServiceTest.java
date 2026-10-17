package com.dev.nexusfinance.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de {@link PasswordService}.
 *
 * <p>Os dois primeiros casos vinham de {@code NexusFinanceApplicationTests}, que
 * apesar do nome nao carregava contexto Spring algum (QA-17). O teste de contexto
 * de verdade vive em {@code ApplicationContextIT}, que precisa de banco.
 */
class PasswordServiceTest {
    private final PasswordService service = new PasswordService();

    @Test
    @DisplayName("protege a senha e valida o hash gerado")
    void protegeEValidaSenha() {
        String hash = service.hash("senha-segura");

        assertThat(hash).isNotEqualTo("senha-segura");
        assertThat(service.matches("senha-segura", hash)).isTrue();
        assertThat(service.matches("senha-errada", hash)).isFalse();
    }

    @Test
    @DisplayName("gera salt diferente a cada hash da mesma senha")
    void geraSaltAleatorio() {
        String primeiro = service.hash("senha-segura");
        String segundo = service.hash("senha-segura");

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(service.matches("senha-segura", primeiro)).isTrue();
        assertThat(service.matches("senha-segura", segundo)).isTrue();
    }

    @Test
    @DisplayName("grava o formato pbkdf2$iteracoes$salt$hash")
    void usaFormatoComIteracoesESalt() {
        String[] partes = service.hash("senha-segura").split("\\$");

        assertThat(partes).hasSize(4);
        assertThat(partes[0]).isEqualTo("pbkdf2");
        assertThat(Integer.parseInt(partes[1])).isEqualTo(210_000);
        assertThat(partes[2]).isNotEmpty();
        assertThat(partes[3]).isNotEmpty();
    }

    @Test
    @DisplayName("rejeita hash com numero de campos invalido")
    void rejeitaHashMalformado() {
        assertThat(service.matches("senha-segura", "pbkdf2$210000$salt")).isFalse();
        assertThat(service.matches("senha-segura", "pbkdf2$210000$salt$hash$extra")).isFalse();
    }

    @Test
    @DisplayName("hash nulo nao autentica e nao lanca excecao")
    void toleraHashNulo() {
        assertThat(service.matches("senha-segura", null)).isFalse();
    }

    /**
     * Caminho de compatibilidade: quando o hash armazenado nao tem o prefixo
     * {@code pbkdf2$}, o servico compara a senha com o valor bruto.
     *
     * <p>Este teste fixa o comportamento atual em vez de aprova-lo. Ele existe
     * para que a remocao desse caminho seja uma decisao consciente: hoje ele
     * permite que uma senha gravada em texto puro (por migracao antiga ou seed)
     * ainda autentique. Ver QA-24 em docs/PLANO_TESTES_E_QUALIDADE.md.
     */
    @ParameterizedTest(name = "senha em texto puro \"{0}\" autentica contra si mesma")
    @ValueSource(strings = {"senha-segura", "123456", "admin"})
    @DisplayName("caminho legado compara texto puro — comportamento fixado, nao endossado")
    void comparaTextoPuroQuandoHashNaoTemPrefixo(String senhaEmTextoPuro) {
        assertThat(service.matches(senhaEmTextoPuro, senhaEmTextoPuro)).isTrue();
        assertThat(service.matches("outra-senha", senhaEmTextoPuro)).isFalse();
    }
}
