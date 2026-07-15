package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(RuntimeException e) { return error(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<Map<String, Object>> unauthorized(RuntimeException e) { return error(HttpStatus.UNAUTHORIZED, e.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception e) { return error(HttpStatus.BAD_REQUEST, e.getMessage()); }
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now().toString(), "status", status.value(), "message", message));
    }
}
