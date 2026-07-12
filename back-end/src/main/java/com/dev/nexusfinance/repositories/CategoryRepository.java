package com.dev.nexusfinance.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.nexusfinance.models.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameIgnoreCase(String name);
}
