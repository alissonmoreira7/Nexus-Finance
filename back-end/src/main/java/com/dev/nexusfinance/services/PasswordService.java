package com.dev.nexusfinance.services;

import org.springframework.stereotype.Service;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class PasswordService {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
            + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String password, String encoded) {
        if (encoded == null || !encoded.startsWith("pbkdf2$")) {
            return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                encoded == null ? new byte[0] : encoded.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4) return false;
        byte[] actual = derive(password.toCharArray(), Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]), actual);
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível proteger a senha", exception);
        }
    }
}
