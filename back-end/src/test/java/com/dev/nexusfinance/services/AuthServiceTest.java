package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private UserRepository users;
    private AuthService auth;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        PasswordService passwords = new PasswordService();
        auth = new AuthService(users, passwords, "test-secret-with-at-least-32-characters");

        user = new User();
        user.setIdUser(UUID.randomUUID());
        user.setName("Ana Silva");
        user.setEmail("ana@email.com");
        user.setPassword(passwords.hash("senha-segura"));
    }

    @Test
    void autenticaEGeraTokenValido() {
        when(users.findByEmailIgnoreCase("ana@email.com")).thenReturn(Optional.of(user));
        when(users.findById(user.getIdUser())).thenReturn(Optional.of(user));

        AuthService.LoginResult result = auth.login("  ana@email.com ", "senha-segura");
        AuthService.AuthenticatedUser authenticated = auth.validate(result.token());

        assertEquals(user.getIdUser(), result.user().id());
        assertEquals(user.getIdUser(), authenticated.id());
    }

    @Test
    void rejeitaSenhaIncorretaSemExporQualCredencialFalhou() {
        when(users.findByEmailIgnoreCase("ana@email.com")).thenReturn(Optional.of(user));

        UnauthorizedException exception = assertThrows(
            UnauthorizedException.class,
            () -> auth.login("ana@email.com", "senha-errada")
        );

        assertEquals("Credenciais inválidas", exception.getMessage());
    }

    @Test
    void rejeitaTokenAdulterado() {
        when(users.findByEmailIgnoreCase("ana@email.com")).thenReturn(Optional.of(user));
        AuthService.LoginResult result = auth.login("ana@email.com", "senha-segura");
        String tampered = result.token().substring(0, result.token().length() - 1) + "x";

        assertThrows(UnauthorizedException.class, () -> auth.validate(tampered));
    }
}
