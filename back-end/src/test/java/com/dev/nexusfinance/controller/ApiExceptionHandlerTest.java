package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de {@link ApiExceptionHandler}.
 *
 * <p>O handler de erro e o ultimo lugar onde se quer um bug: uma falha aqui
 * converte qualquer erro tratado num 500 opaco. Esta classe estava com 0% de
 * cobertura e tinha justamente esse defeito (BUG-02).
 */
class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("recurso nao encontrado vira 404 com a mensagem do dominio")
    void notFound() {
        ResponseEntity<Map<String, Object>> resposta =
            handler.notFound(new ResourceNotFoundException("Transação não encontrada"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody())
            .containsEntry("status", 404)
            .containsEntry("message", "Transação não encontrada")
            .containsKey("timestamp");
    }

    @Test
    @DisplayName("nao autorizado vira 401")
    void unauthorized() {
        ResponseEntity<Map<String, Object>> resposta =
            handler.unauthorized(new UnauthorizedException("Credenciais inválidas"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody()).containsEntry("message", "Credenciais inválidas");
    }

    @Test
    @DisplayName("argumento invalido vira 400")
    void badRequest() {
        ResponseEntity<Map<String, Object>> resposta =
            handler.badRequest(new IllegalArgumentException("CPF deve conter 11 dígitos"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry("message", "CPF deve conter 11 dígitos");
    }

    /**
     * BUG-02: o corpo da resposta era montado com {@code Map.of}, que rejeita
     * valor nulo. Uma excecao sem mensagem fazia o proprio handler lancar
     * {@code NullPointerException} — e o cliente recebia 500 em vez do status
     * pretendido. Jackson e {@code UUID.fromString} produzem esse caso.
     */
    @ParameterizedTest(name = "{0} sem mensagem mantem o status pretendido")
    @MethodSource("excecoesSemMensagem")
    @DisplayName("BUG-02: excecao sem mensagem nao derruba o handler")
    void excecaoSemMensagemNaoDerrubaOHandler(String nome, RuntimeException excecao,
                                              HttpStatus statusEsperado) {
        ResponseEntity<Map<String, Object>> resposta = switch (statusEsperado) {
            case NOT_FOUND -> handler.notFound(excecao);
            case UNAUTHORIZED -> handler.unauthorized(excecao);
            default -> handler.badRequest(excecao);
        };

        assertThat(resposta.getStatusCode()).isEqualTo(statusEsperado);
        assertThat(resposta.getBody())
            .as("mensagem nula deve virar o reason phrase do status, nao NullPointerException")
            .containsEntry("message", statusEsperado.getReasonPhrase());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> excecoesSemMensagem() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(
                "IllegalArgumentException", new IllegalArgumentException(), HttpStatus.BAD_REQUEST),
            org.junit.jupiter.params.provider.Arguments.of(
                "ResourceNotFoundException", new ResourceNotFoundException(null), HttpStatus.NOT_FOUND),
            org.junit.jupiter.params.provider.Arguments.of(
                "UnauthorizedException", new UnauthorizedException(null), HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("BUG-02: mensagem em branco tambem vira o reason phrase")
    void mensagemEmBrancoViraReasonPhrase() {
        ResponseEntity<Map<String, Object>> resposta =
            handler.badRequest(new IllegalArgumentException("   "));

        assertThat(resposta.getBody()).containsEntry("message", "Bad Request");
    }

    /**
     * BUG-03: {@code DELETE /users/me} batia na FK {@code ON DELETE RESTRICT} de
     * {@code tb_accounts} e subia {@code DataIntegrityViolationException}, sem
     * handler — 500 com stack trace. 409 descreve o conflito corretamente.
     */
    @Test
    @DisplayName("BUG-03: violacao de integridade vira 409, nao 500")
    void violacaoDeIntegridadeVira409() {
        ResponseEntity<Map<String, Object>> resposta =
            handler.conflict(new DataIntegrityViolationException("FK constraint fails"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody())
            .containsEntry("status", 409)
            .containsEntry("message", "A operação conflita com dados já existentes");
    }

    @Test
    @DisplayName("nao vaza detalhe interno do banco na mensagem de conflito")
    void naoVazaDetalheDoBanco() {
        ResponseEntity<Map<String, Object>> resposta = handler.conflict(
            new DataIntegrityViolationException(
                "Cannot delete or update a parent row: FK (`nexus_finance_db`.`tb_accounts`)"));

        assertThat(resposta.getBody()).extracting("message").asString()
            .doesNotContain("tb_accounts")
            .doesNotContain("nexus_finance_db");
    }

    @Test
    @DisplayName("o corpo de erro tem sempre as tres mesmas chaves")
    void corpoDeErroTemContratoEstavel() {
        assertThat(handler.badRequest(new IllegalArgumentException("qualquer")).getBody())
            .containsOnlyKeys("timestamp", "status", "message");
    }
}
