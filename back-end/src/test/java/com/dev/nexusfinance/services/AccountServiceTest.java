package com.dev.nexusfinance.services;

import com.dev.nexusfinance.exceptions.ResourceNotFoundException;
import com.dev.nexusfinance.exceptions.UnauthorizedException;
import com.dev.nexusfinance.fixtures.FinanceFixture;
import com.dev.nexusfinance.fixtures.UserFixture;
import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link AccountService}, com enfase em {@code assertOwnership} — o
 * metodo em que repousa toda a autorizacao de recursos financeiros da API.
 */
class AccountServiceTest {
    private AccountRepository accounts;
    private UserRepository users;
    private AccountService service;
    private User ana;
    private User bruno;
    private Account contaDaAna;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        users = mock(UserRepository.class);
        service = new AccountService(accounts, users);

        ana = UserFixture.ana();
        bruno = UserFixture.bruno();
        contaDaAna = FinanceFixture.conta(ana);

        when(users.findById(ana.getIdUser())).thenReturn(Optional.of(ana));
        when(accounts.findById(contaDaAna.getIdAccount())).thenReturn(Optional.of(contaDaAna));
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("cria conta vinculada ao usuario e apara o nome do banco")
    void criaContaParaUsuario() {
        Account criada = service.create(ana.getIdUser(), "  Banco do Brasil  ");

        assertThat(criada.getBankName()).isEqualTo("Banco do Brasil");
        assertThat(criada.getUser()).isSameAs(ana);
        verify(accounts).save(criada);
    }

    @ParameterizedTest(name = "nome do banco invalido: [{0}]")
    @CsvSource(nullValues = "NULL", value = {"NULL", "''", "'   '"})
    @DisplayName("exige nome do banco")
    void exigeNomeDoBanco(String nomeInvalido) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.create(ana.getIdUser(), nomeInvalido))
            .withMessage("Nome do banco é obrigatório");
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("exige usuario existente para criar conta")
    void exigeUsuarioExistente() {
        UUID inexistente = UUID.randomUUID();
        when(users.findById(inexistente)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.create(inexistente, "Banco Teste"))
            .withMessage("Usuário não encontrado");
    }

    @Test
    @DisplayName("encontra conta por id")
    void encontraPorId() {
        assertThat(service.findById(contaDaAna.getIdAccount())).isSameAs(contaDaAna);
    }

    @Test
    @DisplayName("lanca not found para conta inexistente")
    void rejeitaContaInexistente() {
        UUID inexistente = UUID.randomUUID();
        when(accounts.findById(inexistente)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.findById(inexistente))
            .withMessage("Conta não encontrada");
    }

    @Test
    @DisplayName("lista as contas do usuario")
    void listaContasDoUsuario() {
        when(accounts.findByUser_IdUser(ana.getIdUser())).thenReturn(List.of(contaDaAna));

        assertThat(service.findByUser(ana.getIdUser())).containsExactly(contaDaAna);
    }

    @Test
    @DisplayName("devolve lista vazia para usuario sem contas")
    void listaVaziaParaUsuarioSemContas() {
        when(accounts.findByUser_IdUser(bruno.getIdUser())).thenReturn(List.of());

        assertThat(service.findByUser(bruno.getIdUser())).isEmpty();
    }

    @Test
    @DisplayName("assertOwnership aceita o dono da conta")
    void aceitaDonoDaConta() {
        service.assertOwnership(contaDaAna.getIdAccount(), ana.getIdUser());
    }

    @Test
    @DisplayName("assertOwnership recusa usuario que nao e dono")
    void recusaUsuarioQueNaoEhDono() {
        assertThatExceptionOfType(UnauthorizedException.class)
            .isThrownBy(() -> service.assertOwnership(contaDaAna.getIdAccount(), bruno.getIdUser()))
            .withMessage("Acesso negado a esta conta");
    }

    @Test
    @DisplayName("assertOwnership de conta inexistente e not found, nao acesso negado")
    void contaInexistenteEhNotFound() {
        UUID inexistente = UUID.randomUUID();
        when(accounts.findById(inexistente)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.assertOwnership(inexistente, ana.getIdUser()));
    }

    @Test
    @DisplayName("exclui conta existente")
    void excluiContaExistente() {
        when(accounts.existsById(contaDaAna.getIdAccount())).thenReturn(true);

        service.delete(contaDaAna.getIdAccount());

        verify(accounts).deleteById(contaDaAna.getIdAccount());
    }

    @Test
    @DisplayName("lanca not found antes de excluir conta inexistente")
    void rejeitaExclusaoDeContaInexistente() {
        UUID inexistente = UUID.randomUUID();
        when(accounts.existsById(inexistente)).thenReturn(false);

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.delete(inexistente))
            .withMessage("Conta não encontrada");
        verify(accounts, never()).deleteById(any());
    }
}
