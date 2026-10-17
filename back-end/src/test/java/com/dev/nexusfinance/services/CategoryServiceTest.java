package com.dev.nexusfinance.services;

import com.dev.nexusfinance.fixtures.FinanceFixture;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.repositories.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {
    private CategoryRepository categories;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        service = new CategoryService(categories);
        when(categories.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(categories.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("lista todas as categorias")
    void listaTodas() {
        List<Category> esperadas = FinanceFixture.dicionarioPadrao();
        when(categories.findAll()).thenReturn(esperadas);

        assertThat(service.findAll()).isEqualTo(esperadas);
    }

    @Test
    @DisplayName("cria categoria aparando nome e palavras-chave")
    void criaCategoriaNormalizada() {
        Category nova = FinanceFixture.categoria("  Lazer  ", CategoryType.EXPENSE, "  CINEMA,TEATRO  ");

        Category salva = service.create(nova);

        assertThat(salva.getName()).isEqualTo("Lazer");
        assertThat(salva.getKeywords()).isEqualTo("CINEMA,TEATRO");
        verify(categories).save(salva);
    }

    @ParameterizedTest(name = "{0} -> \"{4}\"")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "nome nulo         | NULL  | EXPENSE | CINEMA | Nome da categoria é obrigatório",
        "nome vazio        | ''    | EXPENSE | CINEMA | Nome da categoria é obrigatório",
        "nome em branco    | '   ' | EXPENSE | CINEMA | Nome da categoria é obrigatório",
        "tipo nulo         | Lazer | NULL    | CINEMA | Tipo da categoria é obrigatório",
        "keywords nulas    | Lazer | EXPENSE | NULL   | Palavras-chave são obrigatórias",
        "keywords vazias   | Lazer | EXPENSE | ''     | Palavras-chave são obrigatórias",
        "keywords em branco| Lazer | EXPENSE | '   '  | Palavras-chave são obrigatórias"
    })
    @DisplayName("rejeita categoria com campo obrigatorio ausente")
    void rejeitaCampoObrigatorioAusente(String caso, String nome, CategoryType tipo,
                                        String keywords, String mensagem) {
        Category invalida = FinanceFixture.categoria(nome, tipo, keywords);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.create(invalida))
            .withMessage(mensagem);
        verify(categories, never()).save(any());
    }

    @Test
    @DisplayName("rejeita categoria com nome ja cadastrado, ignorando caixa")
    void rejeitaNomeDuplicado() {
        Category existente = FinanceFixture.categoria("Lazer", CategoryType.EXPENSE, "CINEMA");
        when(categories.findByNameIgnoreCase("Lazer")).thenReturn(Optional.of(existente));

        Category duplicada = FinanceFixture.categoria("  Lazer  ", CategoryType.EXPENSE, "TEATRO");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.create(duplicada))
            .withMessage("Categoria já cadastrada");
        verify(categories, never()).save(any());
    }

    @Test
    @DisplayName("consulta duplicidade pelo nome ja aparado")
    void consultaDuplicidadePeloNomeAparado() {
        service.create(FinanceFixture.categoria("  Lazer  ", CategoryType.EXPENSE, "CINEMA"));

        verify(categories).findByNameIgnoreCase("Lazer");
    }
}
