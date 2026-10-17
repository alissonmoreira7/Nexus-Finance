package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.models.Account;
import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.repositories.AccountRepository;
import com.dev.nexusfinance.repositories.UserRepository;
import com.dev.nexusfinance.services.MonthlyForecastService;
import com.dev.nexusfinance.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Testes das constraints do banco (QA-11).
 *
 * <p>As migrations declaram {@code CHECK}, {@code UNIQUE} e chaves estrangeiras
 * {@code ON DELETE RESTRICT}. Nada disso e exercitado por teste unitario — o
 * mock de repositorio aceita qualquer coisa. Aqui o banco e quem responde.
 */
class DatabaseConstraintsIT extends AbstractIntegrationIT {

    @Autowired
    private TestDataFactory dados;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private UserService userService;

    @Autowired
    private MonthlyForecastService forecasts;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User ana;

    @BeforeEach
    void prepararUsuario() {
        dados.limparTudo();
        jdbcTemplate.update("delete from tb_planned_expenses");
        jdbcTemplate.update("delete from tb_monthly_forecasts");
        jdbcTemplate.update("delete from tb_users");
        ana = dados.usuario("Ana Silva", "ana@email.com", "12345678901");
    }

    @Test
    @DisplayName("UNIQUE de e-mail e aplicada pelo banco, nao so pelo service")
    void emailDuplicadoEhRecusadoPeloBanco() {
        User clone = new User();
        clone.setName("Outra Ana");
        clone.setEmail("ana@email.com");
        clone.setCpf("99988877766");
        clone.setPassword("hash-qualquer");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
            .isThrownBy(() -> users.saveAndFlush(clone));
    }

    @Test
    @DisplayName("UNIQUE de CPF e aplicada pelo banco")
    void cpfDuplicadoEhRecusadoPeloBanco() {
        User clone = new User();
        clone.setName("Outra Ana");
        clone.setEmail("outra@email.com");
        clone.setCpf("12345678901");
        clone.setPassword("hash-qualquer");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
            .isThrownBy(() -> users.saveAndFlush(clone));
    }

    /**
     * V3: {@code CHECK (expected_income >= 0)}. O service ja valida, mas a
     * constraint e a ultima linha de defesa — e precisa existir de fato no schema,
     * nao apenas no arquivo de migration.
     */
    @Test
    @DisplayName("V3: CHECK recusa renda prevista negativa direto no SQL")
    void checkRecusaRendaNegativa() {
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> jdbcTemplate.update("""
            insert into tb_monthly_forecasts (id_forecast, id_user, reference_month, expected_income, version)
            values (UUID_TO_BIN(UUID()), ?, '2026-08-01', -1.00, 0)
            """, uuidBytes(ana)));
    }

    @Test
    @DisplayName("V3: CHECK recusa gasto previsto com valor zero ou negativo")
    void checkRecusaGastoNaoPositivo() {
        forecasts.updateIncome(ana.getIdUser(), YearMonth.of(2026, 8), new BigDecimal("5000.00"));
        byte[] idForecast = jdbcTemplate.queryForObject(
            "select id_forecast from tb_monthly_forecasts where id_user = ?", byte[].class, uuidBytes(ana));

        assertThatExceptionOfType(Exception.class).isThrownBy(() -> jdbcTemplate.update("""
            insert into tb_planned_expenses (id_planned_expense, id_forecast, description, amount, version)
            values (UUID_TO_BIN(UUID()), ?, 'Invalido', 0.00, 0)
            """, idForecast));
    }

    @Test
    @DisplayName("UNIQUE uk_forecast_user_month impede dois planejamentos no mesmo mes")
    void umPlanejamentoPorUsuarioEMes() {
        forecasts.updateIncome(ana.getIdUser(), YearMonth.of(2026, 8), new BigDecimal("5000.00"));
        byte[] id = uuidBytes(ana);

        assertThatExceptionOfType(Exception.class).isThrownBy(() -> jdbcTemplate.update("""
            insert into tb_monthly_forecasts (id_forecast, id_user, reference_month, expected_income, version)
            values (UUID_TO_BIN(UUID()), ?, '2026-08-01', 1000.00, 0)
            """, id));
    }

    @Test
    @DisplayName("o mesmo usuario pode ter planejamentos em meses diferentes")
    void mesesDiferentesSaoPermitidos() {
        forecasts.updateIncome(ana.getIdUser(), YearMonth.of(2026, 8), new BigDecimal("5000.00"));
        forecasts.updateIncome(ana.getIdUser(), YearMonth.of(2026, 9), new BigDecimal("5200.00"));

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from tb_monthly_forecasts where id_user = ?", Integer.class, uuidBytes(ana)))
            .isEqualTo(2);
    }

    /**
     * BUG-03: {@code UserService.delete} faz {@code deleteById} direto. A entidade
     * {@code User} nao tem nenhum {@code @OneToMany}, e as FKs de
     * {@code tb_accounts} e {@code tb_monthly_forecasts} sao {@code ON DELETE
     * RESTRICT}. Resultado: todo usuario que ja criou uma conta nao pode ser
     * excluido — e antes do handler de {@code DataIntegrityViolationException}
     * isso virava 500 com stack trace.
     *
     * <p>Nenhum teste unitario pega isto: o mock de repositorio aceita o delete
     * sem reclamar. So o banco real recusa.
     */
    @Test
    @DisplayName("BUG-03: usuario com conta nao pode ser excluido (FK RESTRICT)")
    void usuarioComContaNaoPodeSerExcluido() {
        dados.conta(ana, "Banco da Ana");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
            .as("a FK ON DELETE RESTRICT de tb_accounts impede a exclusao")
            .isThrownBy(() -> {
                userService.delete(ana.getIdUser());
                users.flush();
            });

        assertThat(users.existsById(ana.getIdUser())).isTrue();
    }

    @Test
    @DisplayName("BUG-03: usuario com planejamento mensal tambem nao pode ser excluido")
    void usuarioComPlanejamentoNaoPodeSerExcluido() {
        forecasts.updateIncome(ana.getIdUser(), YearMonth.of(2026, 8), new BigDecimal("5000.00"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
            .isThrownBy(() -> {
                userService.delete(ana.getIdUser());
                users.flush();
            });
    }

    @Test
    @DisplayName("usuario sem dados vinculados e excluido normalmente")
    void usuarioSemVinculosEhExcluido() {
        userService.delete(ana.getIdUser());

        assertThat(users.existsById(ana.getIdUser())).isFalse();
    }

    /**
     * V4: a FK de {@code tb_user_profile_images} e {@code ON DELETE CASCADE} — ao
     * contrario das outras. Confirma que a foto acompanha a exclusao do usuario.
     */
    @Test
    @DisplayName("V4: a foto de perfil e removida em cascata com o usuario")
    void fotoDePerfilCaiEmCascata() {
        jdbcTemplate.update("""
            insert into tb_user_profile_images (id_user, image_data, content_type)
            values (?, ?, 'image/jpeg')
            """, uuidBytes(ana), new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        userService.delete(ana.getIdUser());
        users.flush();

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from tb_user_profile_images", Integer.class)).isZero();
    }

    @Test
    @DisplayName("conta nao pode referenciar usuario inexistente")
    void contaExigeUsuarioExistente() {
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> jdbcTemplate.update("""
            insert into tb_accounts (id_account, bank_name, id_user)
            values (UUID_TO_BIN(UUID()), 'Banco Fantasma', UUID_TO_BIN(UUID()))
            """));
    }

    @Test
    @DisplayName("ddl-auto=validate confirma que o mapeamento JPA casa com o schema")
    void mapeamentoCasaComOSchema() {
        // O contexto so sobe se validate passar; este teste documenta a garantia
        // e falha de forma legivel se alguem trocar a propriedade.
        List<String> colunas = jdbcTemplate.queryForList("""
            select column_name from information_schema.columns
            where table_schema = database() and table_name = 'tb_transactions'
            """, String.class);

        assertThat(colunas).contains("id_transaction", "id_account", "id_category",
            "raw_description", "clean_description", "amount", "transaction_date", "transaction_source");
    }

    @Test
    @DisplayName("as quatro migrations do projeto estao registradas e aplicadas")
    void migrationsRegistradas() {
        List<String> versoes = jdbcTemplate.queryForList(
            "select version from flyway_schema_history where success = 1 order by installed_rank",
            String.class);

        assertThat(versoes).containsExactly("1", "2", "3", "4");
    }

    private byte[] uuidBytes(User user) {
        return jdbcTemplate.queryForObject(
            "select id_user from tb_users where email = ?", byte[].class, user.getEmail());
    }
}
