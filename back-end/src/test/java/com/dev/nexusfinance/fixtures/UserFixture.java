package com.dev.nexusfinance.fixtures;

import com.dev.nexusfinance.models.PlanType;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserRole;
import com.dev.nexusfinance.services.PasswordService;

import java.util.UUID;

/**
 * Construtores de {@link User} para teste.
 *
 * <p>Existe para que cada teste declare apenas o que ele <em>varia</em>, e nao
 * os cinco campos obrigatorios que ele nao usa. Dois usuarios nomeados
 * ({@code ana} e {@code bruno}) cobrem o caso mais frequente da suite:
 * verificar que um nao acessa o recurso do outro.
 */
public final class UserFixture {
    public static final String SENHA_PADRAO = "senha-segura";

    /**
     * PBKDF2 com 210.000 iteracoes custa ~100ms. Hashear a senha padrao uma vez
     * por JVM, em vez de uma vez por usuario criado, mantem a suite rapida.
     */
    private static final String HASH_SENHA_PADRAO = new PasswordService().hash(SENHA_PADRAO);

    private UserFixture() {
    }

    /** Usuaria dona dos recursos nos testes de autorizacao. */
    public static User ana() {
        return comId(novo("Ana Silva", "ana@email.com", "12345678901"));
    }

    /** Usuario invasor nos testes de autorizacao: autenticado, mas dono de nada. */
    public static User bruno() {
        return comId(novo("Bruno Souza", "bruno@email.com", "98765432100"));
    }

    /** Sem id: para o fluxo de criacao, onde o id e gerado pelo banco. */
    public static User novo(String nome, String email, String cpf) {
        User user = new User();
        user.setName(nome);
        user.setEmail(email);
        user.setCpf(cpf);
        user.setPassword(HASH_SENHA_PADRAO);
        user.setRole(UserRole.USER);
        user.setPlan(PlanType.BASIC);
        return user;
    }

    /** Com senha em texto puro, para o fluxo de cadastro que ainda vai hashear. */
    public static User paraCadastro(String nome, String email, String cpf, String senhaCrua) {
        User user = novo(nome, email, cpf);
        user.setPassword(senhaCrua);
        return user;
    }

    public static User comId(User user) {
        user.setIdUser(UUID.randomUUID());
        return user;
    }
}
