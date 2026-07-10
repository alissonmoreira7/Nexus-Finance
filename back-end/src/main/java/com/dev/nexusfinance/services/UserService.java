package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User create(User user) {
        boolean cpfJaExiste = userRepository.findByCpf(user.getCpf()).isPresent();

        if (cpfJaExiste) {
            throw new RuntimeException("CPF já cadastrado: " + user.getCpf());
        }

        return userRepository.save(user);
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + id));
    }

    public User findByCpf(String cpf) {
        return userRepository.findByCpf(cpf)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado com CPF: " + cpf));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("Usuário não encontrado: " + id);
        }
        userRepository.deleteById(id);
    }
}
