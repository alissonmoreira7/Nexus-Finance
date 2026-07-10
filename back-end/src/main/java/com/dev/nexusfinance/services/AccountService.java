package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    public Account create(UUID userId, String bankName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + userId));

        Account account = new Account();
        account.setUser(user);
        account.setBankName(bankName);

        return accountRepository.save(account);
    }

    public Account findById(UUID id) {
        return accountRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Conta não encontrada: " + id));
    }

    public List<Account> findByUser(UUID userId) {
        return accountRepository.findByUser_IdUser(userId);
    }

    public void delete(UUID id) {
        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Conta não encontrada: " + id);
        }
        accountRepository.deleteById(id);
    }
}
