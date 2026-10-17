package com.dev.nexusfinance.fixtures;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.models.TransactionSource;
import com.dev.nexusfinance.models.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Construtores de contas, categorias e transacoes para teste. */
public final class FinanceFixture {

    private FinanceFixture() {
    }

    public static Account conta(User dono) {
        Account account = new Account();
        account.setIdAccount(UUID.randomUUID());
        account.setBankName("Banco Teste");
        account.setUser(dono);
        return account;
    }

    /** Sem id: para o fluxo de criacao, onde o id e gerado pelo banco. */
    public static Account contaNova(User dono, String banco) {
        Account account = new Account();
        account.setBankName(banco);
        account.setUser(dono);
        return account;
    }

    public static Category categoria(String nome, CategoryType tipo, String keywords) {
        Category category = new Category();
        category.setName(nome);
        category.setType(tipo);
        category.setKeywords(keywords);
        return category;
    }

    /**
     * Dicionario minimo porem completo: dois tipos com keyword e os dois
     * fallbacks que {@code categorize} exige ("Outros" e "Outras entradas").
     * Sem os fallbacks o motor lanca {@code IllegalStateException}.
     */
    public static List<Category> dicionarioPadrao() {
        return List.of(
            categoria("Transporte", CategoryType.EXPENSE, "UBER,TAXI,POSTO"),
            categoria("Alimentacao", CategoryType.EXPENSE, "IFOOD,RESTAURANTE"),
            categoria("Salario", CategoryType.INCOME, "SALARIO"),
            categoria("Outras entradas", CategoryType.INCOME, "RECEBIMENTO,REEMBOLSO"),
            categoria("Outros", CategoryType.EXPENSE, "SEM CATEGORIA")
        );
    }

    public static Transaction transacao(Account conta, Category categoria, String valor, LocalDate data) {
        Transaction transaction = new Transaction();
        transaction.setIdTransaction(UUID.randomUUID());
        transaction.setAccount(conta);
        transaction.setCategory(categoria);
        transaction.setRaw_description("COMPRA VISA*1234 IFOOD SAO PAULO");
        transaction.setClean_description("IFOOD");
        transaction.setAmount(new BigDecimal(valor));
        transaction.setTransaction_date(data);
        transaction.setSource(TransactionSource.CSV);
        return transaction;
    }
}
