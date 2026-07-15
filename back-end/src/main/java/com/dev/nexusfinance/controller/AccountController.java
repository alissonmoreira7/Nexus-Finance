package com.dev.nexusfinance.controller;

import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.services.AccountService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @PostMapping public ResponseEntity<AccountView> create(@RequestAttribute UUID authenticatedUserId, @RequestBody CreateAccount request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(view(service.create(authenticatedUserId, request.bankName())));
    }
    @GetMapping("/user/{userId}") public List<AccountView> byUser(@RequestAttribute UUID authenticatedUserId, @PathVariable UUID userId) {
        if (!authenticatedUserId.equals(userId)) throw new UnauthorizedException("Acesso negado");
        return service.findByUser(userId).stream().map(this::view).toList();
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@RequestAttribute UUID authenticatedUserId, @PathVariable UUID id) { service.assertOwnership(id, authenticatedUserId); service.delete(id); return ResponseEntity.noContent().build(); }
    private AccountView view(Account a) { return new AccountView(a.getIdAccount(), a.getBankName(), a.getUser().getIdUser()); }
    public record CreateAccount(String bankName) {}
    public record AccountView(UUID id, String bankName, UUID userId) {}
}
