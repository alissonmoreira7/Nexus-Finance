package com.dev.nexusfinance.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base dos testes de integracao (QA-11).
 *
 * <p>Sobe um MySQL real e deixa o Flyway aplicar as quatro migrations do projeto,
 * com {@code ddl-auto=validate} ligado. Isso cobre tres coisas que nenhum teste
 * anterior cobria:
 *
 * <ul>
 *   <li>as migrations aplicam numa base limpa, na ordem, sem erro;</li>
 *   <li>o mapeamento JPA corresponde ao schema que as migrations criam;</li>
 *   <li>as JPQL customizadas de fato executam — JPQL quebra em runtime, nao na compilacao.</li>
 * </ul>
 *
 * <p>H2 nao serviria aqui: as migrations usam {@code BINARY(16)}, {@code ENUM},
 * {@code MEDIUMBLOB}, {@code ENGINE=InnoDB} e a collation {@code utf8mb4_0900_ai_ci}.
 * Testar contra um banco diferente do de producao e testar outro sistema.
 *
 * <h2>Por que o container nao usa {@code @Testcontainers}/{@code @Container}</h2>
 *
 * <p>Este e o <em>singleton container pattern</em>, e nao e preferencia de estilo.
 * Com as anotacoes do JUnit, a extensao do Testcontainers <strong>para o
 * container ao fim de cada classe de teste</strong> — mas o Spring mantem o
 * contexto em cache e o reaproveita na classe seguinte, ainda apontando para a
 * porta do container que acabou de morrer. O sintoma e enganoso:
 *
 * <pre>HikariPool-1 - Connection is not available, request timed out
 * after 10003ms (total=0, active=0, idle=0, waiting=0)</pre>
 *
 * <p>O {@code total=0} e a pista — nenhuma conexao foi sequer criada, porque nao
 * ha nada escutando naquela porta. Parece pool esgotado e nao e.
 *
 * <p>Iniciando o container num bloco estatico e nunca parando-o, ele vive
 * enquanto a JVM de teste viver: uma unica instancia para toda a suite. A
 * limpeza fica por conta do Ryuk, o container acompanhante que o Testcontainers
 * sobe para remover recursos orfaos quando a JVM encerra.
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.clean-disabled=false",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
public abstract class AbstractIntegrationIT {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
        .withDatabaseName("nexus_finance_test");

    static {
        MYSQL.start();
    }
}
