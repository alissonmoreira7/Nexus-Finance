package com.dev.nexusfinance.models;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tb_users")
@Getter @Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id_user", updatable=false, nullable=false)
    private UUID idUser;

    @Column(nullable=false, length=100)
    private String name;

    @Column(nullable=false, unique = true, length = 11)
    private String cpf;

    @Column(nullable=false, unique=true, length=100)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;
}
