package com.dev.nexusfinance.services;

import com.dev.nexusfinance.dto.UserView;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserProfileImage;
import com.dev.nexusfinance.repositories.UserProfileImageRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {
    private UserRepository users;
    private UserProfileImageRepository images;
    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        images = mock(UserProfileImageRepository.class);
        service = new UserService(users, images, null);
        user = new User();
        user.setIdUser(UUID.randomUUID());
        user.setName("Nome Antigo");
        user.setEmail("antigo@email.com");
        user.setCpf("12345678901");
        when(users.findById(user.getIdUser())).thenReturn(Optional.of(user));
        when(images.findById(user.getIdUser())).thenReturn(Optional.empty());
    }

    @Test
    void atualizaDadosEArmazenaFotoValida() {
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};

        UserView result = service.updateProfile(
            user.getIdUser(), "  Ana   Silva  ", " ANA@EMAIL.COM ", "987.654.321-00",
            jpeg, "image/jpeg", false
        );

        assertEquals("Ana Silva", result.name());
        assertEquals("ana@email.com", result.email());
        assertEquals("98765432100", result.cpf());
        ArgumentCaptor<UserProfileImage> image = ArgumentCaptor.forClass(UserProfileImage.class);
        verify(images).save(image.capture());
        assertEquals("image/jpeg", image.getValue().getContentType());
    }

    @Test
    void rejeitaArquivoDisfarcadoDeImagem() {
        byte[] invalid = "arquivo executável".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            service.updateProfile(user.getIdUser(), user.getName(), user.getEmail(), user.getCpf(),
                invalid, "image/jpeg", false)
        );

        assertEquals("Envie uma foto JPEG, PNG ou WebP válida", exception.getMessage());
    }
}
