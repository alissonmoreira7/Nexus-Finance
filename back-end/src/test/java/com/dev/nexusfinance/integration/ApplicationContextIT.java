package com.dev.nexusfinance.integration;

import com.dev.nexusfinance.config.CategorySeeder;
import com.dev.nexusfinance.controller.TransactionController;
import com.dev.nexusfinance.services.AuthService;
import com.dev.nexusfinance.services.MonthlyForecastService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste de contexto que faltava (QA-17).
 *
 * <p>Havia um arquivo chamado {@code NexifFinanceApplicationTests} que, apesar do
 * nome, nao tinha {@code @SpringBootTest}: eram dois testes unitarios soltos. Na
 * pratica o contexto Spring nunca subia em teste, e erro de wiring, bean faltando
 * ou {@code @Value} sem default passavam pelo build sem serem notados.
 */
class ApplicationContextIT extends AbstractIntegrationIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("o contexto da aplicacao sobe por completo")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }

    @Test
    @DisplayName("os beans de dominio sao resolviveis")
    void beansDeDominioExistem() {
        assertThat(context.getBean(AuthService.class)).isNotNull();
        assertThat(context.getBean(MonthlyForecastService.class)).isNotNull();
        assertThat(context.getBean(TransactionController.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
    }

    @Test
    @DisplayName("o container MySQL esta de pe e acessivel")
    void bancoEstaAcessivel() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }

    /**
     * QA-05: o seeder e um {@code CommandLineRunner} sem guarda de perfil, o que
     * fazia ele gravar 6 categorias em todo start de contexto — inclusive em teste.
     * Com {@code @Profile("!test")} ele fica fora, e o estado do banco em teste
     * passa a ser so o que cada teste monta.
     */
    @Test
    @DisplayName("QA-05: CategorySeeder nao e ativado no perfil de teste")
    void seederNaoRodaEmTeste() {
        assertThat(context.getBeanNamesForType(CategorySeeder.class)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from tb_categories", Integer.class))
            .as("nenhuma categoria deve ser semeada implicitamente em teste")
            .isZero();
    }

    @Test
    @DisplayName("o perfil de teste nao aponta para o banco de desenvolvimento")
    void naoUsaBancoDeDesenvolvimento() {
        String url = jdbcTemplate.execute(
            (org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                connection.getMetaData().getURL());

        assertThat(url)
            .as("o teste deve usar a porta efemera do container, nunca a 3307 do dev")
            .doesNotContain("3307")
            .contains(String.valueOf(MYSQL.getFirstMappedPort()));
    }

    @Test
    @DisplayName("todas as tabelas do schema existem depois das migrations")
    void schemaCompletoFoiCriado() {
        List<String> tabelas = jdbcTemplate.queryForList(
            "select table_name from information_schema.tables where table_schema = database()",
            String.class);

        assertThat(tabelas).contains(
            "tb_users", "tb_accounts", "tb_categories", "tb_transactions",
            "tb_monthly_forecasts", "tb_planned_expenses", "tb_user_profile_images",
            "flyway_schema_history");
    }
}
