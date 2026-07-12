package com.dev.nexusfinance.controller;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.services.CategoryService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }
    @GetMapping public List<Category> all() { return service.findAll(); }
    @PostMapping public ResponseEntity<Category> create(@RequestBody Category category) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(category)); }
}
