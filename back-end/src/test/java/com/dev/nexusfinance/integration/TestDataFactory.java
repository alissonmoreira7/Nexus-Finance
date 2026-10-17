package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.models.TransactionSource;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.CategoryRepository;
import com.dev.nexusfinance.repositories.TransactionRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import com.dev.nexusfinance.services.PasswordService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Semeia dados reais no banco do container.
 *
 * <p>Diferente das fixtures de unidade, que montam objetos em memoria, esta
 * fabrica persiste — os testes de integracao precisam de linhas de verdade para
 * exercitar JPQL, constraints e chaves estrangeiras.
 */
@Component
public class TestDataFactory {
    public static final String SENHA = "senha-segura";

    private final UserRepository users;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final PasswordService passwords;

    public TestDataFactory(UserRepository users, AccountRepository accounts,
                           CategoryRepository categories, TransactionRepository transactions,
                           PasswordService passwords) {
        this.users = users;
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.passwords = passwords;
    }

    public void limparTudo() {
        transactions.deleteAll();
        accounts.deleteAll();
        categories.deleteAll();
        users.deleteAll();
    }

    public User usuario(String nome, String email, String cpf) {
        User user = new User();
        user.setName(nome);
        user.setEmail(email);
        user.setCpf(cpf);
        user.setPassword(passwords.hash(SENHA));
        return users.save(user);
    }

    public Account conta(User dono, String banco) {
        Account account = new Account();
        account.setUser(dono);
        account.setBankName(banco);
        return accounts.save(account);
    }

    /** Dicionario com os dois fallbacks que o motor de categorizacao exige. */
    public List<Category> dicionario() {
        return List.of(
            categoria("Transporte", CategoryType.EXPENSE, "UBER,TAXI,POSTO"),
            categoria("Alimentacao", CategoryType.EXPENSE, "IFOOD,RESTAURANTE"),
            categoria("Salario", CategoryType.INCOME, "SALARIO"),
            categoria("Outras entradas", CategoryType.INCOME, "RECEBIMENTO,REEMBOLSO"),
            categoria("Outros", CategoryType.EXPENSE, "SEM CATEGORIA"));
    }

    public Category categoria(String nome, CategoryType tipo, String keywords) {
        Category category = new Category();
        category.setName(nome);
        category.setType(tipo);
        category.setKeywords(keywords);
        return categories.save(category);
    }

    public Transaction transacao(Account conta, Category categoria, String descricao,
                                 String valor, LocalDate data, TransactionSource origem) {
        Transaction transaction = new Transaction();
        transaction.setAccount(conta);
        transaction.setCategory(categoria);
        transaction.setRaw_description("COMPRA " + descricao);
        transaction.setClean_description(descricao);
        transaction.setAmount(new BigDecimal(valor));
        transaction.setTransaction_date(data);
        transaction.setSource(origem);
        return transactions.save(transaction);
    }
}
