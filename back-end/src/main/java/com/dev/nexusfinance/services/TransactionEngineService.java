package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.*;
import com.dev.nexusfinance.repositories.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class TransactionEngineService {
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    public TransactionEngineService(TransactionRepository transactions, AccountRepository accounts, CategoryRepository categories) {
        this.transactions = transactions; this.accounts = accounts; this.categories = categories;
    }
    public record TransactionInput(LocalDate date, String rawDescription, BigDecimal amount) {}

    @Transactional
    public int processLote(UUID accountId, List<TransactionInput> inputs) {
        return process(accountId, inputs, TransactionSource.CSV);
    }

    @Transactional
    public Transaction processManual(UUID accountId, TransactionInput input) {
        return processAndReturn(accountId, List.of(input), TransactionSource.MANUAL).get(0);
    }

    private int process(UUID accountId, List<TransactionInput> inputs, TransactionSource source) {
        return processAndReturn(accountId, inputs, source).size();
    }

    private List<Transaction> processAndReturn(UUID accountId, List<TransactionInput> inputs, TransactionSource source) {
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("O lote deve conter ao menos uma transação");
        if (inputs.size() > 10_000) throw new IllegalArgumentException("O lote excede o limite de 10.000 transações");
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        List<Category> dictionary = categories.findAll();
        if (dictionary.isEmpty()) throw new IllegalStateException("Cadastre ao menos uma categoria antes de importar transações");
        List<Transaction> toSave = new ArrayList<>(inputs.size());
        for (TransactionInput input : inputs) {
            if (input == null || input.date() == null || input.rawDescription() == null || input.rawDescription().isBlank()
                || input.amount() == null || input.amount().signum() < 0) throw new IllegalArgumentException("Cada transação deve ter data, descrição e valor não negativo");
            String clean = cleanDescription(input.rawDescription());
            Transaction tx = new Transaction(); tx.setAccount(account); tx.setCategory(categorize(clean, dictionary));
            tx.setRaw_description(input.rawDescription()); tx.setClean_description(clean); tx.setAmount(input.amount()); tx.setTransaction_date(input.date());
            tx.setSource(source);
            toSave.add(tx);
        }
        return transactions.saveAll(toSave);
    }
    public String cleanDescription(String raw) {
        if (raw == null || raw.isBlank()) return "SEM DESCRICAO";
        return raw.toUpperCase().replaceAll("\\*\\d+", "")
            .replaceAll("\\b(COMPRA|PAGAMENTO|TRANSFERENCIA|PIX|DEBITO|CREDITO|VISA|MASTER|ELO)\\b", "")
            .replaceAll("\\b(SAO PAULO|RIO DE JANEIRO|BELO HORIZONTE|CURITIBA|BRASILIA|SP|RJ|MG|PR|DF|BA|PE)\\b", "")
            .replaceAll("\\b\\d{4,}\\b", "").replaceAll("\\s{2,}", " ").trim();
    }
    public Category categorize(String description, List<Category> dictionary) {
        String upper = description.toUpperCase();
        for (Category category : dictionary) {
            if (category.getKeywords() != null && Arrays.stream(category.getKeywords().split(","))
                .map(String::trim).filter(keyword -> !keyword.isEmpty()).anyMatch(keyword -> upper.contains(keyword.toUpperCase()))) return category;
        }
        return dictionary.stream().filter(c -> c.getName().equalsIgnoreCase("Outros")).findFirst()
            .orElseThrow(() -> new IllegalStateException("Cadastre uma categoria chamada Outros"));
    }
    @Transactional(readOnly = true)
    public Page<Transaction> findByAccount(UUID accountId, TransactionSource source, Pageable pageable) {
        if (!accounts.existsById(accountId)) throw new ResourceNotFoundException("Conta não encontrada");
        return source == null ? transactions.findByAccount_IdAccount(accountId, pageable)
            : transactions.findByAccountAndSource(accountId, source, pageable);
    }
}
