package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.*;
import com.dev.nexusfinance.repositories.CategoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    public CategoryService(CategoryRepository categories) { this.categories = categories; }
    public List<Category> findAll() { return categories.findAll(); }
    public Category create(Category category) {
        if (category.getName() == null || category.getName().isBlank()) throw new IllegalArgumentException("Nome da categoria é obrigatório");
        if (category.getType() == null) throw new IllegalArgumentException("Tipo da categoria é obrigatório");
        if (category.getKeywords() == null || category.getKeywords().isBlank()) throw new IllegalArgumentException("Palavras-chave são obrigatórias");
        if (categories.findByNameIgnoreCase(category.getName().trim()).isPresent()) throw new IllegalArgumentException("Categoria já cadastrada");
        category.setName(category.getName().trim()); category.setKeywords(category.getKeywords().trim());
        return categories.save(category);
    }
}
