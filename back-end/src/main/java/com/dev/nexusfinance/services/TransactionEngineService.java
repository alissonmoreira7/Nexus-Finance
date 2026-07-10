package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.CategoryRepository;
import com.dev.nexusfinance.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionEngineService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    public record TransactionInput(
        LocalDate date,
        String rawDescription,
        BigDecimal amount
    ) {}

    // ── Processar lote de transações ──────────────────────────────
    public int processLote(UUID accountId, List<TransactionInput> inputs) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Conta não encontrada: " + accountId));

        List<Category> categories = categoryRepository.findAll();
        List<Transaction> toSave = new ArrayList<>();

        for (TransactionInput input : inputs) {
            String clean = cleanDescription(input.rawDescription());
            Category category = categorize(clean, categories);

            Transaction tx = new Transaction();
            tx.setAccount(account);
            tx.setCategory(category);
            tx.setRaw_description(input.rawDescription());
            tx.setClean_description(clean);
            tx.setAmount(input.amount());
            tx.setTransaction_date(input.date());

            toSave.add(tx);
        }

        transactionRepository.saveAll(toSave);
        return toSave.size();
    }

    public String cleanDescription(String raw) {
        if (raw == null || raw.isBlank()) return "SEM DESCRICAO";

        return raw.toUpperCase()
            // remove *1234 (código de terminal)
            .replaceAll("\\*\\d+", "")
            // remove prefixos de compra/pix/transferência
            .replaceAll("\\b(COMPRA|PAGAMENTO|TRANSFERENCIA|PIX|DEBITO|CREDITO|VISA|MASTER|ELO)\\b", "")
            // remove cidades brasileiras comuns
            .replaceAll("\\b(SAO PAULO|RIO DE JANEIRO|BELO HORIZONTE|CURITIBA|BRASILIA|SP|RJ|MG|PR|DF|BA|PE)\\b", "")
            // remove números soltos (ex: terminais, datas)
            .replaceAll("\\b\\d{4,}\\b", "")
            // remove espaços duplos
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    public Category categorize(String cleanDesc, List<Category> categories) {
        String upper = cleanDesc.toUpperCase();

        for (Category cat : categories) {
            if (cat.getKeywords() == null) continue;

            String[] keywords = cat.getKeywords().split(",");
            for (String kw : keywords) {
                if (upper.contains(kw.trim().toUpperCase())) {
                    return cat;
                }
            }
        }

        return categories.stream()
            .filter(c -> c.getName().equalsIgnoreCase("Outros"))
            .findFirst()
            .orElse(categories.get(0));
    }

    public List<Transaction> findByAccount(UUID accountId) {
        return transactionRepository.findByAccount_IdAccount(accountId);
    }
}
