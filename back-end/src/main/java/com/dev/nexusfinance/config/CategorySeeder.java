package com.dev.nexusfinance.config;
import com.dev.nexusfinance.models.*;
import com.dev.nexusfinance.repositories.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Semeia as categorias padrao no start da aplicacao.
 *
 * <p>Desativado no perfil {@code test}: em teste, o estado do banco e montado
 * explicitamente por cada teste, e um seeder implicito tornaria os testes de
 * integracao dependentes de ordem de execucao.
 */
@Profile("!test")
@Component
public class CategorySeeder implements CommandLineRunner {
    private final CategoryRepository categories;
    public CategorySeeder(CategoryRepository categories) { this.categories = categories; }
    @Override public void run(String... args) {
        seed("Alimentação", CategoryType.EXPENSE, "IFOOD,MCDONALDS,BURGER KING,RESTAURANTE,SUPERMERCADO");
        seed("Transporte", CategoryType.EXPENSE, "UBER,99APP,TAXI,POSTO,COMBUSTIVEL");
        seed("Moradia", CategoryType.EXPENSE, "ALUGUEL,CONDOMINIO,ENERGIA,AGUA");
        seed("Salário", CategoryType.INCOME, "SALARIO,PAGAMENTO EMPRESA");
        seed("Outras entradas", CategoryType.INCOME, "RECEBIMENTO,REEMBOLSO,RESTITUICAO");
        seed("Outros", CategoryType.EXPENSE, "SEM CATEGORIA");
    }
    private void seed(String name, CategoryType type, String keywords) {
        if (categories.findByNameIgnoreCase(name).isEmpty()) {
            Category category = new Category(); category.setName(name); category.setType(type); category.setKeywords(keywords); categories.save(category);
        }
    }
}
