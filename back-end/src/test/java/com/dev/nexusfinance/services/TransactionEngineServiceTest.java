package com.dev.nexusfinance.services;

import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionEngineServiceTest {
    private final TransactionEngineService service = new TransactionEngineService(null, null, null);
    private final Category transport = category("Transporte", CategoryType.EXPENSE, "UBER,TAXI");
    private final Category salary = category("Salário", CategoryType.INCOME, "SALARIO");
    private final Category otherExpense = category("Outros", CategoryType.EXPENSE, "SEM CATEGORIA");
    private final Category otherIncome = category("Outras entradas", CategoryType.INCOME, "REEMBOLSO");
    private final List<Category> dictionary = List.of(transport, salary, otherExpense, otherIncome);

    @Test
    void categorizaSomenteDentroDoTipoSelecionado() {
        assertSame(transport, service.categorize("UBER", dictionary, CategoryType.EXPENSE));
        assertSame(otherIncome, service.categorize("UBER", dictionary, CategoryType.INCOME));
        assertSame(salary, service.categorize("SALARIO", dictionary, CategoryType.INCOME));
        assertSame(otherExpense, service.categorize("SALARIO", dictionary, CategoryType.EXPENSE));
    }

    @Test
    void mantemInferenciaDeTipoParaImportacaoSemTipo() {
        assertSame(transport, service.categorize("UBER", dictionary));
        assertSame(salary, service.categorize("SALARIO", dictionary));
    }

    @Test
    void exigeTipoNoLancamentoManual() {
        TransactionEngineService.TransactionInput input = new TransactionEngineService.TransactionInput(
            LocalDate.now(), "PIX RECEBIDO", BigDecimal.TEN, null
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.processManual(null, input)
        );

        assertEquals("Informe se a transação é uma entrada ou saída", exception.getMessage());
    }

    private static Category category(String name, CategoryType type, String keywords) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setKeywords(keywords);
        return category;
    }
}
