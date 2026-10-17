package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(RuntimeException e) { return error(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<Map<String, Object>> unauthorized(RuntimeException e) { return error(HttpStatus.UNAUTHORIZED, e.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception e) { return error(HttpStatus.BAD_REQUEST, e.getMessage()); }

    /**
     * Violacao de constraint do banco — tipicamente uma FK {@code ON DELETE RESTRICT}
     * ou uma UNIQUE em corrida. Sem este handler o cliente recebia 500 com stack
     * trace; 409 descreve o caso corretamente. Ver BUG-03 no plano de testes.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "A operação conflita com dados já existentes");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        // Map.of rejeita valor nulo: uma excecao sem mensagem fazia o proprio handler
        // lancar NullPointerException, devolvendo 500 em vez do status pretendido.
        // Ver BUG-02 no plano de testes.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
