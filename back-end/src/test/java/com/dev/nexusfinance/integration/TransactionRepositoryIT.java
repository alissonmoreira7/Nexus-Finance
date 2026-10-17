package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.models.TransactionSource;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes das JPQL customizadas de {@link TransactionRepository} (QA-11).
 *
 * <p>Sao cinco consultas escritas a mao, incluindo uma ({@code searchByAccount})
 * com tres filtros opcionais e tratamento de {@code null} em parametro de enum.
 * JPQL nao e verificada em tempo de compilacao: um erro de sintaxe ou de nome de
 * campo so aparece quando a query executa. Nenhuma delas tinha teste.
 */
@Transactional
class TransactionRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private TestDataFactory dados;

    private Account conta;
    private Account outraConta;
    private Category transporte;
    private Category alimentacao;
    private Category salario;

    private static final LocalDate MES = LocalDate.of(2026, 8, 1);

    @BeforeEach
    void semearExtrato() {
        dados.limparTudo();

        User ana = dados.usuario("Ana Silva", "ana@email.com", "12345678901");
        User bruno = dados.usuario("Bruno Souza", "bruno@email.com", "98765432100");
        conta = dados.conta(ana, "Banco da Ana");
        outraConta = dados.conta(bruno, "Banco do Bruno");

        List<Category> dicionario = dados.dicionario();
        transporte = dicionario.get(0);
        alimentacao = dicionario.get(1);
        salario = dicionario.get(2);

        dados.transacao(conta, alimentacao, "IFOOD", "89.90", MES.withDayOfMonth(10), TransactionSource.CSV);
        dados.transacao(conta, transporte, "UBER TRIP", "25.50", MES.withDayOfMonth(12), TransactionSource.CSV);
        dados.transacao(conta, transporte, "POSTO SHELL", "200.00", MES.withDayOfMonth(15), TransactionSource.MANUAL);
        dados.transacao(conta, salario, "SALARIO", "5000.00", MES.withDayOfMonth(5), TransactionSource.MANUAL);
        // Fora da janela de apuracao e de outra conta: devem ficar de fora dos resultados.
        dados.transacao(conta, alimentacao, "RESTAURANTE ANTIGO", "50.00",
            MES.minusMonths(1).withDayOfMonth(20), TransactionSource.CSV);
        dados.transacao(outraConta, alimentacao, "IFOOD DO BRUNO", "999.00",
            MES.withDayOfMonth(10), TransactionSource.CSV);
    }

    @Test
    @DisplayName("searchByAccount sem filtro devolve so as transacoes da conta")
    void buscaSemFiltroIsolaPorConta() {
        Page<Transaction> pagina = transactions.searchByAccount(
            conta.getIdAccount(), null, null, "", PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getContent())
            .extracting(Transaction::getClean_description)
            .doesNotContain("IFOOD DO BRUNO");
    }

    @Test
    @DisplayName("searchByAccount ordena por data decrescente")
    void ordenaPorDataDecrescente() {
        Page<Transaction> pagina = transactions.searchByAccount(
            conta.getIdAccount(), null, null, "", PageRequest.of(0, 20));

        assertThat(pagina.getContent())
            .extracting(Transaction::getTransaction_date)
            .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @ParameterizedTest(name = "filtro source={0} devolve {1} transacoes")
    @CsvSource({"CSV, 3", "MANUAL, 2"})
    @DisplayName("searchByAccount filtra por origem")
    void filtraPorOrigem(TransactionSource origem, int esperado) {
        assertThat(transactions.searchByAccount(
            conta.getIdAccount(), origem, null, "", PageRequest.of(0, 20)).getTotalElements())
            .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "filtro type={0} devolve {1} transacoes")
    @CsvSource({"EXPENSE, 4", "INCOME, 1"})
    @DisplayName("searchByAccount filtra por tipo da categoria")
    void filtraPorTipo(CategoryType tipo, int esperado) {
        assertThat(transactions.searchByAccount(
            conta.getIdAccount(), null, tipo, "", PageRequest.of(0, 20)).getTotalElements())
            .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "busca por \"{0}\" devolve {1}")
    @CsvSource({
        "ifood, 1",
        "IFOOD, 1",
        "uber, 1",
        "transporte, 2",
        "TRANSPORTE, 2",
        "inexistente, 0"
    })
    @DisplayName("searchByAccount busca na descricao limpa e no nome da categoria, sem distinguir caixa")
    void buscaPorTextoEmDescricaoECategoria(String termo, int esperado) {
        assertThat(transactions.searchByAccount(
            conta.getIdAccount(), null, null, termo, PageRequest.of(0, 20)).getTotalElements())
            .isEqualTo(esperado);
    }

    @Test
    @DisplayName("searchByAccount combina os tres filtros")
    void combinaTresFiltros() {
        Page<Transaction> pagina = transactions.searchByAccount(
            conta.getIdAccount(), TransactionSource.MANUAL, CategoryType.EXPENSE,
            "posto", PageRequest.of(0, 20));

        assertThat(pagina.getContent()).singleElement()
            .extracting(Transaction::getClean_description).isEqualTo("POSTO SHELL");
    }

    @Test
    @DisplayName("searchByAccount pagina corretamente")
    void paginaCorretamente() {
        Page<Transaction> primeira = transactions.searchByAccount(
            conta.getIdAccount(), null, null, "", PageRequest.of(0, 2));

        assertThat(primeira.getContent()).hasSize(2);
        assertThat(primeira.getTotalElements()).isEqualTo(5);
        assertThat(primeira.getTotalPages()).isEqualTo(3);
        assertThat(primeira.hasNext()).isTrue();
    }

    /**
     * A query trata {@code source is null} como "transacao legada importada por
     * CSV". Esse ramo da JPQL nunca tinha sido executado.
     */
    @Test
    @DisplayName("transacao legada com source nulo e tratada como CSV")
    void sourceNuloContaComoCsv() {
        Transaction legada = dados.transacao(conta, alimentacao, "LEGADA", "10.00",
            MES.withDayOfMonth(2), null);
        assertThat(legada.getSource()).isNull();

        assertThat(transactions.searchByAccount(
            conta.getIdAccount(), TransactionSource.CSV, null, "", PageRequest.of(0, 20)).getTotalElements())
            .as("a transacao sem origem deve aparecer no filtro CSV")
            .isEqualTo(4);
    }

    @Test
    @DisplayName("findDetailedById carrega categoria e conta no mesmo acesso")
    void detalheCarregaRelacionamentos() {
        Transaction qualquer = transactions.searchByAccount(
            conta.getIdAccount(), null, null, "ifood", PageRequest.of(0, 1)).getContent().get(0);

        Transaction detalhada = transactions.findDetailedById(qualquer.getIdTransaction()).orElseThrow();

        assertThat(detalhada.getCategory().getName()).isEqualTo("Alimentacao");
        assertThat(detalhada.getAccount().getBankName()).isEqualTo("Banco da Ana");
    }

    @Test
    @DisplayName("findDetailedById devolve vazio para id inexistente")
    void detalheDeIdInexistente() {
        assertThat(transactions.findDetailedById(java.util.UUID.randomUUID())).isEmpty();
    }

    @ParameterizedTest(name = "soma de {0} no mes e {1}")
    @CsvSource({"INCOME, 5000.00", "EXPENSE, 315.40"})
    @DisplayName("sumByType soma apenas a janela do mes e o tipo pedido")
    void somaPorTipoNaJanelaDoMes(CategoryType tipo, BigDecimal esperado) {
        BigDecimal soma = transactions.sumByType(
            conta.getIdAccount(), MES, MES.plusMonths(1), tipo);

        assertThat(soma).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("sumByType devolve zero, nao nulo, quando nada casa")
    void somaDevolveZeroQuandoVazio() {
        BigDecimal soma = transactions.sumByType(
            conta.getIdAccount(), MES.plusYears(5), MES.plusYears(5).plusMonths(1), CategoryType.INCOME);

        assertThat(soma).isNotNull().isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sumByType nao soma transacao de outra conta")
    void somaIsolaPorConta() {
        BigDecimal soma = transactions.sumByType(
            outraConta.getIdAccount(), MES, MES.plusMonths(1), CategoryType.EXPENSE);

        assertThat(soma).isEqualByComparingTo("999.00");
    }

    @Test
    @DisplayName("sumExpensesByCategory agrupa so despesas, em ordem decrescente de valor")
    void agrupaDespesasPorCategoria() {
        List<Object[]> linhas = transactions.sumExpensesByCategory(
            conta.getIdAccount(), MES, MES.plusMonths(1));

        assertThat(linhas).hasSize(2);
        assertThat((String) linhas.get(0)[0]).isEqualTo("Transporte");
        assertThat((BigDecimal) linhas.get(0)[1]).isEqualByComparingTo("225.50");
        assertThat((String) linhas.get(1)[0]).isEqualTo("Alimentacao");
        assertThat((BigDecimal) linhas.get(1)[1]).isEqualByComparingTo("89.90");
    }

    @Test
    @DisplayName("sumExpensesByCategory nao inclui categoria de entrada")
    void naoAgrupaEntradas() {
        List<Object[]> linhas = transactions.sumExpensesByCategory(
            conta.getIdAccount(), MES, MES.plusMonths(1));

        assertThat(linhas).extracting(linha -> (String) linha[0]).doesNotContain("Salario");
    }

    @Test
    @DisplayName("findByAccount_IdAccount pagina e ordena por data decrescente")
    void listaPorContaOrdenada() {
        Page<Transaction> pagina = transactions.findByAccount_IdAccount(
            conta.getIdAccount(), PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getContent())
            .extracting(Transaction::getTransaction_date)
            .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("findByAccountAndSource filtra por origem")
    void listaPorContaEOrigem() {
        assertThat(transactions.findByAccountAndSource(
            conta.getIdAccount(), TransactionSource.MANUAL, PageRequest.of(0, 20)).getTotalElements())
            .isEqualTo(2);
    }
}
