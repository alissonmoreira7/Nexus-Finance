package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
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

}
