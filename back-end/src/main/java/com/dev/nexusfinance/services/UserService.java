package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public UserService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    @Transactional
    public User create(User user) {
        validate(user);
        String cpf = user.getCpf().replaceAll("\\D", "");
        String email = user.getEmail().trim().toLowerCase();
        if (userRepository.findByCpf(cpf).isPresent()) throw new IllegalArgumentException("CPF já cadastrado");
        if (userRepository.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("E-mail já cadastrado");
        user.setName(user.getName().trim());
        user.setCpf(cpf);
        user.setEmail(email);
        user.setPassword(passwordService.hash(user.getPassword()));
        return userRepository.save(user);
    }

    public User findById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
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
}
