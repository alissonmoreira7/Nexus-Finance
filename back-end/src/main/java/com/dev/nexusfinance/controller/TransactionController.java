package com.dev.nexusfinance.controller;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.services.TransactionEngineService;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionEngineService service;
    private final com.dev.nexusfinance.services.AccountService accounts;
    public TransactionController(TransactionEngineService service, com.dev.nexusfinance.services.AccountService accounts) { this.service = service; this.accounts = accounts; }
    @PostMapping("/upload") public ResponseEntity<UploadResult> upload(@RequestAttribute UUID authenticatedUserId, @RequestParam UUID accountId, @RequestBody List<TransactionEngineService.TransactionInput> inputs) {
        accounts.assertOwnership(accountId, authenticatedUserId);
        int count = service.processLote(accountId, inputs);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResult("Lote processado com sucesso", count));
    }
    @GetMapping("/account/{accountId}") public PageResponse<TransactionView> statement(@RequestAttribute UUID authenticatedUserId, @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        accounts.assertOwnership(accountId, authenticatedUserId);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginação inválida; size deve estar entre 1 e 100");
        Page<Transaction> result = service.findByAccount(accountId, PageRequest.of(page, size, Sort.by("transaction_date").descending()));
        return new PageResponse<>(result.getContent().stream().map(this::view).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    private TransactionView view(Transaction t) { return new TransactionView(t.getIdTransaction(), t.getClean_description(), t.getCategory().getName(), t.getCategory().getType().name(), t.getAmount(), t.getTransaction_date()); }
    public record UploadResult(String message, int inserted) {}
    public record TransactionView(UUID id, String cleanDescription, String category, String type, BigDecimal amount, LocalDate transactionDate) {}
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
