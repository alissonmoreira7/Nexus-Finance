package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.fixtures.FinanceFixture;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.Category;
import com.dev.nexusfinance.models.CategoryType;
import com.dev.nexusfinance.models.Transaction;
import com.dev.nexusfinance.models.TransactionSource;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.CategoryRepository;
import com.dev.nexusfinance.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionEngineServiceTest {
    private TransactionRepository transactions;
    private AccountRepository accounts;
    private CategoryRepository categories;
    private TransactionEngineService service;
    private Account conta;

    private final List<Category> dicionario = FinanceFixture.dicionarioPadrao();

    @BeforeEach
    void setUp() {
        // Mocks em vez de null nas tres dependencias (QA-16): um refactor que passe
        // a usar o repositorio falha de forma legivel, nao com NullPointerException.
        transactions = mock(TransactionRepository.class);
        accounts = mock(AccountRepository.class);
        categories = mock(CategoryRepository.class);
        service = new TransactionEngineService(transactions, accounts, categories);

        conta = FinanceFixture.conta(UserFixture.ana());
        when(accounts.findById(conta.getIdAccount())).thenReturn(Optional.of(conta));
        when(accounts.existsById(conta.getIdAccount())).thenReturn(true);
        when(categories.findAll()).thenReturn(dicionario);
        when(transactions.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("limpeza de descricao bancaria")
    class Limpeza {

        @ParameterizedTest(name = "[{0}] -> [{1}]")
        @CsvSource(delimiter = '|', value = {
            "COMPRA VISA*1234 UBER EATS SAO PAULO | UBER EATS",
            "IFOOD*99 PE                          | IFOOD",
            "SPA DAY RELAX                        | SPA DAY RELAX",
            "PAGAMENTO ALUGUEL                    | ALUGUEL",
            "TRANSFERENCIA JOAO                   | JOAO",
            "MCDONALDS   CURITIBA                 | MCDONALDS"
        })
        @DisplayName("remove ruido bancario e preserva o estabelecimento")
        void removeRuidoBancario(String bruta, String esperada) {
            assertThat(service.cleanDescription(bruta)).isEqualTo(esperada);
        }

        /**
         * BUG-01: quando todos os tokens sao ruido, a limpeza sobrava string vazia.
         * A coluna e NOT NULL, mas "" satisfaz NOT NULL — a transacao era gravada
         * com descricao em branco e o usuario via uma linha vazia no extrato.
         */
        @ParameterizedTest(name = "descricao so com ruido: [{0}]")
        @CsvSource({
            "COMPRA VISA*1234 SAO PAULO",
            "PAGAMENTO DEBITO 123456",
            "PIX",
            "TRANSFERENCIA RJ"
        })
        @DisplayName("BUG-01: descricao composta so de ruido nao resulta em string vazia")
        void naoDevolveDescricaoVazia(String soRuido) {
            assertThat(service.cleanDescription(soRuido))
                .isEqualTo(TransactionEngineService.DESCRICAO_AUSENTE);
        }

        @ParameterizedTest(name = "entrada ausente: [{0}]")
        @CsvSource(value = {"''", "'   '", "NULL"}, nullValues = "NULL")
        @DisplayName("entrada nula ou em branco devolve o marcador padrao")
        void toleraEntradaAusente(String ausente) {
            assertThat(service.cleanDescription(ausente))
                .isEqualTo(TransactionEngineService.DESCRICAO_AUSENTE);
        }
    }

    @Nested
    @DisplayName("categorizacao")
    class Categorizacao {
        private final Category transporte = dicionario.get(0);
        private final Category salario = dicionario.get(2);
        private final Category outrasEntradas = dicionario.get(3);
        private final Category outros = dicionario.get(4);

        @Test
        @DisplayName("categoriza somente dentro do tipo exigido")
        void respeitaTipoExigido() {
            assertThat(service.categorize("UBER", dicionario, CategoryType.EXPENSE)).isSameAs(transporte);
            assertThat(service.categorize("UBER", dicionario, CategoryType.INCOME)).isSameAs(outrasEntradas);
            assertThat(service.categorize("SALARIO", dicionario, CategoryType.INCOME)).isSameAs(salario);
            assertThat(service.categorize("SALARIO", dicionario, CategoryType.EXPENSE)).isSameAs(outros);
        }

        /**
         * Fixa a inferencia por keyword usada na importacao de CSV sem tipo.
         *
         * <p>Este comportamento e o bug B-01 da auditoria: um extrato real traz o
         * sinal no valor, e ignora-lo faz um estorno virar despesa. O teste
         * documenta o estado atual; quando a precedencia
         * (coluna tipo -> sinal do valor -> keyword) for implementada, ele deve ser
         * reescrito para afirmar a nova regra, nao removido. Ver QA-21.
         */
        @Test
        @DisplayName("sem tipo, infere apenas por keyword — comportamento atual, ver B-01")
        void inferePorKeywordQuandoNaoHaTipo() {
            assertThat(service.categorize("UBER", dicionario)).isSameAs(transporte);
            assertThat(service.categorize("SALARIO", dicionario)).isSameAs(salario);
        }

        /**
         * BUG-04: o casamento e {@code contains}, nao limite de palavra. A keyword
         * TAXI casa dentro de TAXIDERMIA. Fixado como comportamento conhecido
         * porque corrigir muda a categorizacao de dados ja importados — e uma
         * decisao de produto, nao um ajuste tecnico.
         */
        @Test
        @DisplayName("BUG-04: keyword casa dentro de outra palavra (falso positivo conhecido)")
        void keywordCasaDentroDeOutraPalavra() {
            assertThat(service.categorize("TAXIDERMIA MUSEU", dicionario, CategoryType.EXPENSE))
                .isSameAs(transporte);
        }

        @Test
        @DisplayName("cai no fallback do tipo quando nenhuma keyword casa")
        void usaFallbackPorTipo() {
            assertThat(service.categorize("ESTABELECIMENTO DESCONHECIDO", dicionario, CategoryType.EXPENSE))
                .isSameAs(outros);
            assertThat(service.categorize("ESTABELECIMENTO DESCONHECIDO", dicionario, CategoryType.INCOME))
                .isSameAs(outrasEntradas);
        }

        @Test
        @DisplayName("exige categoria de fallback cadastrada")
        void exigeFallbackCadastrado() {
            List<Category> semFallback = List.of(
                FinanceFixture.categoria("Transporte", CategoryType.EXPENSE, "UBER"));

            assertThatIllegalStateException()
                .isThrownBy(() -> service.categorize("DESCONHECIDO", semFallback, CategoryType.EXPENSE))
                .withMessageContaining("Outros");
        }

        @Test
        @DisplayName("ignora categoria sem keywords e keyword vazia entre virgulas")
        void ignoraKeywordsAusentes() {
            List<Category> dicionarioImperfeito = List.of(
                FinanceFixture.categoria("Sem keywords", CategoryType.EXPENSE, null),
                FinanceFixture.categoria("Virgulas vazias", CategoryType.EXPENSE, ",, ,"),
                FinanceFixture.categoria("Outros", CategoryType.EXPENSE, "SEM CATEGORIA"));

            assertThat(service.categorize("QUALQUER COISA", dicionarioImperfeito, CategoryType.EXPENSE).getName())
                .isEqualTo("Outros");
        }
    }

    @Nested
    @DisplayName("lancamento manual")
    class LancamentoManual {

        @Test
        @DisplayName("exige o tipo no lancamento manual")
        void exigeTipo() {
            TransactionEngineService.TransactionInput input = new TransactionEngineService.TransactionInput(
                LocalDate.now(), "PIX RECEBIDO", BigDecimal.TEN, null);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.processManual(conta.getIdAccount(), input))
                .withMessage("Informe se a transação é uma entrada ou saída");
        }

        @Test
        @DisplayName("rejeita entrada nula")
        void rejeitaEntradaNula() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.processManual(conta.getIdAccount(), null))
                .withMessage("Informe se a transação é uma entrada ou saída");
        }

        @Test
        @DisplayName("grava origem MANUAL e descricao limpa")
        void gravaOrigemManual() {
            TransactionEngineService.TransactionInput input = new TransactionEngineService.TransactionInput(
                LocalDate.of(2026, 8, 10), "COMPRA VISA*1234 IFOOD SAO PAULO",
                new BigDecimal("89.90"), CategoryType.EXPENSE);

            Transaction resultado = service.processManual(conta.getIdAccount(), input);

            assertThat(resultado.getSource()).isEqualTo(TransactionSource.MANUAL);
            assertThat(resultado.getClean_description()).isEqualTo("IFOOD");
            assertThat(resultado.getRaw_description()).isEqualTo("COMPRA VISA*1234 IFOOD SAO PAULO");
            assertThat(resultado.getAmount()).isEqualByComparingTo("89.90");
            assertThat(resultado.getCategory().getName()).isEqualTo("Alimentacao");
        }
    }

    @Nested
    @DisplayName("importacao em lote")
    class ImportacaoEmLote {

        @Test
        @DisplayName("grava origem CSV e devolve a quantidade inserida")
        void processaLote() {
            List<TransactionEngineService.TransactionInput> lote = List.of(
                entrada("UBER TRIP", "25.50"),
                entrada("IFOOD PEDIDO", "60.00"));

            assertThat(service.processLote(conta.getIdAccount(), lote)).isEqualTo(2);

            ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.captor();
            verify(transactions).saveAll(captor.capture());
            assertThat(captor.getValue())
                .allSatisfy(tx -> assertThat(tx.getSource()).isEqualTo(TransactionSource.CSV))
                .extracting(Transaction::getClean_description)
                .containsExactly("UBER TRIP", "IFOOD PEDIDO");
        }

        @Test
        @DisplayName("rejeita lote nulo ou vazio")
        void rejeitaLoteVazio() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.processLote(conta.getIdAccount(), List.of()))
                .withMessage("O lote deve conter ao menos uma transação");
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.processLote(conta.getIdAccount(), null))
                .withMessage("O lote deve conter ao menos uma transação");
            verify(transactions, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("rejeita lote acima de 10.000 transacoes")
        void rejeitaLoteAcimaDoLimite() {
            List<TransactionEngineService.TransactionInput> gigante =
                java.util.Collections.nCopies(10_001, entrada("UBER", "10.00"));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.processLote(conta.getIdAccount(), gigante))
                .withMessageContaining("limite de 10.000");
        }

        @Test
        @DisplayName("exige conta existente")
        void exigeContaExistente() {
            UUID inexistente = UUID.randomUUID();
            when(accounts.findById(inexistente)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.processLote(inexistente, List.of(entrada("UBER", "10.00"))))
                .withMessage("Conta não encontrada");
        }

        @Test
        @DisplayName("exige pelo menos uma categoria cadastrada")
        void exigeCategoriaCadastrada() {
            when(categories.findAll()).thenReturn(List.of());

            assertThatIllegalStateException()
                .isThrownBy(() -> service.processLote(conta.getIdAccount(), List.of(entrada("UBER", "10.00"))))
                .withMessageContaining("Cadastre ao menos uma categoria");
        }

        @ParameterizedTest(name = "item invalido: {0}")
        @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "sem data        | NULL       | UBER | 10.00",
            "sem descricao   | 2026-08-10 | NULL | 10.00",
            "descricao vazia | 2026-08-10 | ''   | 10.00",
            "sem valor       | 2026-08-10 | UBER | NULL",
            "valor negativo  | 2026-08-10 | UBER | -10.00"
        })
        @DisplayName("rejeita item incompleto ou com valor negativo")
        void rejeitaItemInvalido(String caso, LocalDate data, String descricao, BigDecimal valor) {
            List<TransactionEngineService.TransactionInput> lote = List.of(
                new TransactionEngineService.TransactionInput(data, descricao, valor, CategoryType.EXPENSE));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.processLote(conta.getIdAccount(), lote))
                .withMessageContaining("data, descrição e valor não negativo");
        }

        /**
         * BUG-05: a validacao e {@code signum() < 0}, que rejeita negativo e aceita
         * zero. Uma linha de extrato com R$ 0,00 entra na base e polui o analytics.
         * Fixado como comportamento atual — rejeitar zero e decisao de produto.
         */
        @Test
        @DisplayName("BUG-05: transacao de valor zero e aceita")
        void aceitaValorZero() {
            List<TransactionEngineService.TransactionInput> lote = List.of(entrada("TARIFA", "0.00"));

            assertThat(service.processLote(conta.getIdAccount(), lote)).isEqualTo(1);
        }

        private TransactionEngineService.TransactionInput entrada(String descricao, String valor) {
            return new TransactionEngineService.TransactionInput(
                LocalDate.of(2026, 8, 10), descricao, new BigDecimal(valor), CategoryType.EXPENSE);
        }
    }

    @Nested
    @DisplayName("consulta")
    class Consulta {

        @Test
        @DisplayName("exige conta existente ao listar extrato")
        void exigeContaAoListar() {
            UUID inexistente = UUID.randomUUID();
            when(accounts.existsById(inexistente)).thenReturn(false);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.findByAccount(inexistente, null, null, "", null))
                .withMessage("Conta não encontrada");
        }

        @Test
        @DisplayName("normaliza busca nula para string vazia antes de consultar")
        void normalizaBuscaNula() {
            service.findByAccount(conta.getIdAccount(), null, null, null, null);

            verify(transactions).searchByAccount(conta.getIdAccount(), null, null, "", null);
        }

        @Test
        @DisplayName("remove espacos em volta do termo de busca")
        void removeEspacosDaBusca() {
            service.findByAccount(conta.getIdAccount(), null, null, "  ifood  ", null);

            verify(transactions).searchByAccount(conta.getIdAccount(), null, null, "ifood", null);
        }

        @Test
        @DisplayName("lanca not found quando a transacao nao existe")
        void exigeTransacaoExistente() {
            UUID inexistente = UUID.randomUUID();
            when(transactions.findDetailedById(inexistente)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.findById(inexistente))
                .withMessage("Transação não encontrada");
        }

        @Test
        @DisplayName("devolve a transacao detalhada quando existe")
        void devolveTransacaoDetalhada() {
            Transaction esperada = FinanceFixture.transacao(
                conta, dicionario.get(1), "89.90", LocalDate.of(2026, 8, 10));
            when(transactions.findDetailedById(esperada.getIdTransaction())).thenReturn(Optional.of(esperada));

            assertThat(service.findById(esperada.getIdTransaction())).isSameAs(esperada);
        }

        @Test
        @DisplayName("delega a exclusao ao repositorio")
        void delegaExclusao() {
            UUID id = UUID.randomUUID();

            service.deleteTransaction(id);

            verify(transactions).deleteById(id);
        }

        @Test
        @DisplayName("sobrecarga de dois argumentos repassa filtros neutros")
        void sobrecargaUsaFiltrosNeutros() {
            service.findByAccount(conta.getIdAccount(), TransactionSource.CSV, null);

            verify(transactions).searchByAccount(
                conta.getIdAccount(), TransactionSource.CSV, null, "", null);
        }
    }

    @Test
    @DisplayName("categoriza com o dicionario vindo do repositorio")
    void usaDicionarioDoRepositorio() {
        service.processManual(conta.getIdAccount(), new TransactionEngineService.TransactionInput(
            LocalDate.of(2026, 8, 10), "UBER TRIP", new BigDecimal("25.50"), CategoryType.EXPENSE));

        verify(categories).findAll();
        verify(transactions).saveAll(any());
    }
}
