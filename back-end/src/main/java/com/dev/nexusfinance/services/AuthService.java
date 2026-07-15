package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {
    private static final long TOKEN_TTL_SECONDS = 8 * 60 * 60;
    private final UserRepository users;
    private final PasswordService passwords;
    private final byte[] secret;

    public AuthService(UserRepository users, PasswordService passwords,
                       @Value("${app.auth.secret}") String secret) {
        this.users = users;
        this.passwords = passwords;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public LoginResult login(String email, String password) {
        if (email == null || password == null) throw new IllegalArgumentException("E-mail e senha são obrigatórios");
        User user = users.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
        if (!passwords.matches(password, user.getPassword())) throw new UnauthorizedException("Credenciais inválidas");
        return new LoginResult(createToken(user.getIdUser()), new UserView(user.getIdUser(), user.getName(), user.getEmail()));
    }

    public UUID validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) throw new UnauthorizedException("Token inválido");
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split(":");
            if (values.length != 2 || Instant.now().getEpochSecond() > Long.parseLong(values[1])) throw new UnauthorizedException("Token expirado");
            return UUID.fromString(values[0]);
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnauthorizedException("Token inválido");
        }
    }

    private String createToken(UUID userId) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            (userId + ":" + (Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS)).getBytes(StandardCharsets.UTF_8));
        return payload + "." + sign(payload);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Falha ao assinar token", exception); }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginResult(String token, UserView user) {}
    public record UserView(UUID id, String name, String email) {}
}
