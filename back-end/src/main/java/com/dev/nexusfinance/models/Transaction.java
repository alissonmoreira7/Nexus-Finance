package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_transaction", updatable = false, nullable = false)
    private UUID idTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_account", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private Category category;

    @Column(name = "raw_description", updatable = true, nullable = false)
    private String raw_description;

    @Column(name = "clean_description", updatable = true, nullable = false)
    private String clean_description;

    @Column(name = "amount", updatable = true, nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_date", updatable = true, nullable = false)
    private LocalDate transaction_date;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_source", length = 10)
    private TransactionSource source;

    public UUID getIdTransaction() { return idTransaction; }
    public void setIdTransaction(UUID idTransaction) { this.idTransaction = idTransaction; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getRaw_description() { return raw_description; }
    public void setRaw_description(String rawDescription) { this.raw_description = rawDescription; }
    public String getClean_description() { return clean_description; }
    public void setClean_description(String cleanDescription) { this.clean_description = cleanDescription; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getTransaction_date() { return transaction_date; }
    public void setTransaction_date(LocalDate transactionDate) { this.transaction_date = transactionDate; }
    public TransactionSource getSource() { return source == null ? TransactionSource.CSV : source; }
    public void setSource(TransactionSource source) { this.source = source; }

}
