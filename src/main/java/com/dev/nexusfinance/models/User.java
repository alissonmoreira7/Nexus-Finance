package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tb_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id_user", updatable=false, nullable=false)
    private UUID idUser;

    @Column(nullable=false, length=100)
    private String name;

    @Column(nullable=false, unique = true, length = 11)
    private String cpf;

    @Column(nullable = false)
    private String password;
}
