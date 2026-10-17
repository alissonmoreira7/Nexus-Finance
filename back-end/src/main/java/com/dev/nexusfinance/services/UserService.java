package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.dto.UserView;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserProfileImage;
import com.dev.nexusfinance.models.PlanType;
import com.dev.nexusfinance.models.UserRole;
import com.dev.nexusfinance.repositories.UserRepository;
import com.dev.nexusfinance.repositories.UserProfileImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private static final int MAX_PROFILE_IMAGE_BYTES = 1_000_000;
    private final UserRepository userRepository;
    private final UserProfileImageRepository profileImages;
    private final PasswordService passwordService;

    public UserService(UserRepository userRepository, UserProfileImageRepository profileImages, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.profileImages = profileImages;
        this.passwordService = passwordService;
    }

    @Transactional
    public User create(User user) {
        validate(user);
        // O endpoint de cadastro e publico e recebe a entidade crua. Com idUser
        // preenchido pelo cliente, save() faz merge em vez de persist — um UPDATE
        // na linha daquele UUID, ou seja, takeover de conta sem autenticacao.
        // Ver S-02 na auditoria de seguranca.
        user.setIdUser(null);
        String cpf = user.getCpf().replaceAll("\\D", "");
        String email = user.getEmail().trim().toLowerCase();
        if (userRepository.findByCpf(cpf).isPresent()) throw new IllegalArgumentException("CPF já cadastrado");
        if (userRepository.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("E-mail já cadastrado");
        user.setName(user.getName().trim());
        user.setCpf(cpf);
        user.setEmail(email);
        user.setPassword(passwordService.hash(user.getPassword()));
        user.setRole(UserRole.USER);
        user.setPlan(PlanType.BASIC);
        return userRepository.save(user);
    }

    public User findById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public UserView getProfile(UUID id) {
        User user = findById(id);
        return UserView.from(user, profileImages.findById(id).orElse(null));
    }

    @Transactional
    public UserView updateProfile(UUID id, String name, String email, String cpf,
                                  byte[] imageData, String imageContentType, boolean removeImage) {
        User user = findById(id);
        String normalizedName = normalizeName(name);
        String normalizedEmail = normalizeEmail(email);
        String normalizedCpf = normalizeCpf(cpf);

        if (userRepository.existsByEmailIgnoreCaseAndIdUserNot(normalizedEmail, id)) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }
        if (userRepository.existsByCpfAndIdUserNot(normalizedCpf, id)) {
            throw new IllegalArgumentException("CPF já cadastrado");
        }

        UserProfileImage image = null;
        if (removeImage) {
            if (profileImages.existsById(id)) profileImages.deleteById(id);
        } else if (imageData != null && imageData.length > 0) {
            validateImage(imageData, imageContentType);
            image = profileImages.findById(id).orElseGet(UserProfileImage::new);
            image.setUserId(id);
            image.setData(imageData);
            image.setContentType(imageContentType);
            image = profileImages.save(image);
        } else {
            image = profileImages.findById(id).orElse(null);
        }

        user.setName(normalizedName);
        user.setEmail(normalizedEmail);
        user.setCpf(normalizedCpf);
        userRepository.save(user);
        return UserView.from(user, image);
    }

    public User findByCpf(String cpf) {
        return userRepository.findByCpf(cpf.replaceAll("\\D", ""))
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    public List<User> findAll() { return userRepository.findAll(); }

    public void delete(UUID id) {
        if (!userRepository.existsById(id)) throw new ResourceNotFoundException("Usuário não encontrado");
        userRepository.deleteById(id);
    }

    private void validate(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) throw new IllegalArgumentException("Nome é obrigatório");
        if (user.getCpf() == null || !user.getCpf().replaceAll("\\D", "").matches("\\d{11}")) throw new IllegalArgumentException("CPF deve conter 11 dígitos");
        if (user.getEmail() == null || !user.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("E-mail inválido");
        if (user.getPassword() == null || user.getPassword().length() < 6) throw new IllegalArgumentException("Senha deve ter no mínimo 6 caracteres");
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome é obrigatório");
        String normalized = name.trim().replaceAll("\\s{2,}", " ");
        if (normalized.length() > 100) throw new IllegalArgumentException("Nome deve ter no máximo 100 caracteres");
        return normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("E-mail inválido");
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.length() > 100) throw new IllegalArgumentException("E-mail deve ter no máximo 100 caracteres");
        return normalized;
    }

    private String normalizeCpf(String cpf) {
        String normalized = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (!normalized.matches("\\d{11}")) throw new IllegalArgumentException("CPF deve conter 11 dígitos");
        return normalized;
    }

    private void validateImage(byte[] data, String contentType) {
        if (data.length > MAX_PROFILE_IMAGE_BYTES) {
            throw new IllegalArgumentException("A foto deve ter no máximo 1 MB");
        }
        boolean valid = switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> startsWith(data, 0xff, 0xd8, 0xff);
            case "image/png" -> startsWith(data, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "image/webp" -> startsWith(data, 0x52, 0x49, 0x46, 0x46)
                && data.length >= 12 && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Envie uma foto JPEG, PNG ou WebP válida");
    }

    private boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if ((data[index] & 0xff) != signature[index]) return false;
        }
        return true;
    }
}
