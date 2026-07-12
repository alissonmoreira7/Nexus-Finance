package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final UserRepository users;
    public AccountService(AccountRepository accounts, UserRepository users) { this.accounts = accounts; this.users = users; }
    public Account create(UUID userId, String bankName) {
        if (bankName == null || bankName.isBlank()) throw new IllegalArgumentException("Nome do banco é obrigatório");
        Account account = new Account();
        account.setUser(users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado")));
        account.setBankName(bankName.trim());
        return accounts.save(account);
    }
    public Account findById(UUID id) { return accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada")); }
    public List<Account> findByUser(UUID userId) { return accounts.findByUser_IdUser(userId); }
    public void assertOwnership(UUID accountId, UUID userId) {
        Account account = findById(accountId);
        if (!account.getUser().getIdUser().equals(userId)) throw new UnauthorizedException("Acesso negado a esta conta");
    }
    public void delete(UUID id) { if (!accounts.existsById(id)) throw new ResourceNotFoundException("Conta não encontrada"); accounts.deleteById(id); }
}
