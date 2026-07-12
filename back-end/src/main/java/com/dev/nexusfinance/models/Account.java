package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tb_accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_account", updatable = false, nullable = false)
    private UUID idAccount;

    @Column(name="bank_name", nullable=false, length=100)
    private String bankName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="id_user", nullable = false)
    private User user;

    public UUID getIdAccount() { return idAccount; }
    public void setIdAccount(UUID idAccount) { this.idAccount = idAccount; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}

