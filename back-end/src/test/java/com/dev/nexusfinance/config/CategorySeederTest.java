package com.dev.nexusfinance.config;

import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.repositories.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link CategorySeeder}.
 *
 * <p>Fica fora do contexto de teste por {@code @Profile("!test")} (QA-05), entao
 * e exercitado aqui como unidade: e so um {@code CommandLineRunner}, e
 * {@code run()} pode ser chamado direto.
 *
 * <p>O que importa verificar e a idempotencia: o seeder roda em <em>todo</em>
 * start da aplicacao, inclusive em producao, a cada deploy.
 */
class CategorySeederTest {
    private CategoryRepository categories;
    private CategorySeeder seeder;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        seeder = new CategorySeeder(categories);
        when(categories.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("semeia as seis categorias padrao numa base vazia")
    void semeiaCategoriasPadrao() {
        seeder.run();

        ArgumentCaptor<Category> salvas = ArgumentCaptor.captor();
        verify(categories, org.mockito.Mockito.times(6)).save(salvas.capture());

        assertThat(salvas.getAllValues())
            .extracting(Category::getName)
            .containsExactly("Alimentação", "Transporte", "Moradia",
                "Salário", "Outras entradas", "Outros");
    }

    /**
     * O motor de categorizacao exige uma categoria de fallback por tipo, com nome
     * exato: lanca {@code IllegalStateException} se nao achar "Outros" ou
     * "Outras entradas". O seeder e quem garante que elas existam.
     */
    @Test
    @DisplayName("semeia os dois fallbacks que o motor de categorizacao exige")
    void semeiaOsFallbacksObrigatorios() {
        seeder.run();

        ArgumentCaptor<Category> salvas = ArgumentCaptor.captor();
        verify(categories, org.mockito.Mockito.times(6)).save(salvas.capture());
        List<Category> todas = salvas.getAllValues();

        assertThat(todas)
            .filteredOn(categoria -> categoria.getName().equals("Outros"))
            .singleElement()
            .extracting(Category::getType).isEqualTo(CategoryType.EXPENSE);
        assertThat(todas)
            .filteredOn(categoria -> categoria.getName().equals("Outras entradas"))
            .singleElement()
            .extracting(Category::getType).isEqualTo(CategoryType.INCOME);
    }

    @Test
    @DisplayName("e idempotente: nao duplica categoria que ja existe")
    void naoDuplicaCategoriaExistente() {
        when(categories.findByNameIgnoreCase("Transporte"))
            .thenReturn(Optional.of(new Category()));

        seeder.run();

        ArgumentCaptor<Category> salvas = ArgumentCaptor.captor();
        verify(categories, org.mockito.Mockito.times(5)).save(salvas.capture());
        assertThat(salvas.getAllValues())
            .extracting(Category::getName)
            .doesNotContain("Transporte");
    }

    @Test
    @DisplayName("nao grava nada quando todas as categorias ja existem")
    void naoGravaNadaSeTudoExiste() {
        when(categories.findByNameIgnoreCase(anyString()))
            .thenReturn(Optional.of(new Category()));

        seeder.run();

        verify(categories, never()).save(any());
    }

    @Test
    @DisplayName("toda categoria semeada tem tipo e palavras-chave preenchidos")
    void categoriasSemeadasSaoCompletas() {
        seeder.run();

        ArgumentCaptor<Category> salvas = ArgumentCaptor.captor();
        verify(categories, org.mockito.Mockito.times(6)).save(salvas.capture());

        assertThat(salvas.getAllValues()).allSatisfy(categoria -> {
            assertThat(categoria.getName()).isNotBlank();
            assertThat(categoria.getType()).isNotNull();
            assertThat(categoria.getKeywords()).isNotBlank();
        });
    }

    @Test
    @DisplayName("aceita argumentos de linha de comando sem usa-los")
    void ignoraArgumentosDeLinhaDeComando() {
        seeder.run("--spring.profiles.active=prod", "qualquer");

        verify(categories, org.mockito.Mockito.times(6)).save(any());
    }
}
