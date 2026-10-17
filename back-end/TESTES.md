# Testes — Back-end NexusFinance

Documentação da malha de testes: como rodar, como ela está organizada, e como escrever um teste novo
que caiba no lugar certo.

> Stack de teste: **JUnit Jupiter 6.0.3** · AssertJ 3.27.7 · Mockito 5.23.0 · Testcontainers 2.0.5 ·
> ArchUnit 1.3.0 · JaCoCo 0.8.13 — todos sob Spring Boot 4.1.0 / Java 17.

---

## 1. Como rodar

Todos os comandos rodam de dentro de `back-end/`. Use o wrapper (`./mvnw`), não um Maven instalado.

### O dia a dia

```bash
cd back-end

# Testes unitários e de slice — rápido, sem Docker. É o que você roda ao programar.
./mvnw test

# Tudo: unitários + integração + relatório de cobertura + gate. Precisa de Docker.
./mvnw verify
```

### Recortes

```bash
# Uma classe
./mvnw test -Dtest=AuthServiceTest

# Um método
./mvnw test -Dtest=AuthServiceTest#rejeitaTokenExpirado

# Várias classes (separadas por vírgula, não por "+")
./mvnw test -Dtest=UserServiceTest,UserControllerTest

# Por padrão de nome
./mvnw test -Dtest='*ControllerTest'
```

### Só os testes de integração

```bash
# Todos os *IT (sobe MySQL via Testcontainers)
./mvnw test-compile failsafe:integration-test failsafe:verify

# Um IT específico — note que o Failsafe usa -Dit.test, não -Dtest
./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=AuthorizationMatrixIT
```

### Cobertura

```bash
# O relatório é gerado na fase verify
./mvnw verify

# Para um relatório rápido depois de só rodar os unitários
./mvnw test jacoco:report
```

Abra o resultado em `target/site/jacoco/index.html`. Para o resumo no terminal:

```bash
awk -F, 'NR>1 {lm[$2]+=$8; lc[$2]+=$9; bm[$2]+=$6; bc[$2]+=$7; tlm+=$8; tlc+=$9; tbm+=$6; tbc+=$7}
  END {for (p in lm) {l=lm[p]+lc[p]; b=bm[p]+bc[p];
       printf "%-40s %6.1f%% linha  %6.1f%% branch\n", p, 100*lc[p]/l, (b?100*bc[p]/b:0)}
       printf "%-40s %6.1f%% linha  %6.1f%% branch\n", "TOTAL",
       100*tlc/(tlm+tlc), 100*tbc/(tbm+tbc)}' \
  target/site/jacoco/jacoco.csv | sort
```

### Mutation testing (opcional, lento)

```bash
./mvnw -Pmutation test
# relatório em target/pit-reports/index.html
```

---

## 2. Pré-requisitos

| Para rodar | Precisa |
|---|---|
| `./mvnw test` | apenas JDK 17+ |
| `./mvnw verify` | JDK 17+ **e Docker rodando** |

Os testes de integração sobem um container `mysql:8.4` pelo Testcontainers. Se o Docker estiver
parado, eles falham com `Could not find a valid Docker environment`. No Windows, basta abrir o Docker
Desktop e esperar o daemon subir.

A primeira execução baixa a imagem do MySQL (~600 MB) e leva alguns minutos; as seguintes reaproveitam
a imagem do cache local.

**Nenhum teste depende do banco de desenvolvimento.** O perfil `test` não configura datasource, e os
`*IT` recebem a URL do container via `@ServiceConnection`. Há até um teste que falha de propósito se
alguém apontar a suíte para a porta 3307 do ambiente local
(`ApplicationContextIT.naoUsaBancoDeDesenvolvimento`).

---

## 3. Convenções

### Nomes de arquivo definem onde o teste roda

| Sufixo | Plugin | Fase | Precisa de Docker |
|---|---|---|---|
| `*Test` | Surefire | `test` | não |
| `*IT` | Failsafe | `integration-test` | sim |

Essa separação é deliberada: `./mvnw test` tem que continuar rápido o suficiente para ser rodado a cada
mudança. Um teste de integração nomeado `...Test` por engano torna o feedback local lento e desmotiva
rodar testes — que é o pior resultado possível para uma suíte.

### Nomes de método descrevem a regra, em português

```java
@Test
@DisplayName("rejeita senha incorreta sem revelar qual credencial falhou")
void rejeitaSenhaIncorreta() { ... }
```

O nome diz **a regra de negócio**, não a mecânica. `rejeitaSenhaIncorreta` em vez de
`testLoginReturnsException`. Quando um teste assim quebra, o relatório de falha já explica o que o
sistema deixou de garantir.

### AssertJ, não as asserções do JUnit

```java
// sim
assertThat(resultado.totalPlanned()).isEqualByComparingTo("2350.50");
assertThatIllegalArgumentException().isThrownBy(() -> service.create(invalido))
    .withMessage("CPF deve conter 11 dígitos");

// não
assertEquals(new BigDecimal("2350.50"), resultado.totalPlanned());
```

Em dinheiro isso não é estilo, é correção: `assertEquals` em `BigDecimal` compara a escala, então
`2350.50` e `2350.5` são considerados diferentes. `isEqualByComparingTo` compara o valor.

### Tabela de casos vira `@ParameterizedTest`

Validação é tabela de entrada/saída. Escrita caso a caso, a validação de `UserService` seriam ~25
métodos quase idênticos; parametrizada, são 3 blocos legíveis:

```java
@ParameterizedTest(name = "{0} -> \"{4}\"")
@CsvSource(delimiter = '|', nullValues = "NULL", value = {
    "cpf curto      | Ana | a@b.com      | 123456789   | CPF deve conter 11 dígitos",
    "email sem tld  | Ana | ana@email    | 12345678901 | E-mail inválido"
})
void rejeitaDadosInvalidos(String caso, String nome, String email, String cpf, String mensagem) { ... }
```

A primeira coluna é só o rótulo do caso — aparece no nome do teste e faz o relatório de falha dizer
qual linha da tabela quebrou.

### Mocks, nunca `null`

```java
// sim
service = new TransactionEngineService(mock(TransactionRepository.class), mock(...), mock(...));

// não — funciona por coincidência, até alguém usar a dependência
service = new TransactionEngineService(null, null, null);
```

Com `null`, qualquer refatoração que passe a usar a dependência falha com `NullPointerException` em vez
de uma asserção legível.

---

## 4. Mapa dos testes

**341 testes unitários e de slice** em 16 classes, mais os testes de integração. Camada por camada:

### 4.1 Serviços — lógica de negócio pura

| Classe | Testes | O que cobre |
|---|---:|---|
| `services/UserServiceTest` | 76 | cadastro, normalização de nome/e-mail/CPF, magic bytes dos 3 formatos de imagem, limite de 1 MB, troca e remoção de foto |
| `services/TransactionEngineServiceTest` | 41 | limpeza de descrição bancária, categorização por tipo e fallback, importação em lote, limites e casos de borda |
| `services/MonthlyForecastServiceTest` | 40 | precisão monetária, vencimento dentro do mês, isolamento entre usuários, ordenação dos gastos |
| `services/AuthServiceTest` | 26 | login, assinatura HMAC, token adulterado, **token expirado**, token de usuário deletado |
| `services/AccountServiceTest` | 14 | criação e, principalmente, `assertOwnership` — onde repousa toda a autorização |
| `services/AnalyticsServiceTest` | 12 | janela de apuração do mês, somas nulas, agrupamento por categoria |
| `services/CategoryServiceTest` | 11 | campos obrigatórios, duplicidade ignorando caixa |
| `services/PasswordServiceTest` | 8 | PBKDF2, salt aleatório, formato do hash, caminho legado |

Dois cenários dessa lista só existem porque o `java.time.Clock` passou a ser injetado:
**token expirado** (o TTL é de 8 horas — sem controlar o relógio, o único jeito de testar era esperar
8 horas) e a **virada de mês no analytics**. Em teste injeta-se `Clock.fixed(...)` e o tempo vira
parâmetro.

### 4.2 Controllers — contrato HTTP (`@WebMvcTest`)

| Classe | Testes | O que cobre |
|---|---:|---|
| `controller/RemainingControllersTest` | 31 | contas, analytics, categorias, planejamento e login, em `@Nested` por controller |
| `controller/TransactionControllerTest` | 22 | status codes, shape do JSON, paginação inválida, busca longa, verificação de dono |
| `controller/UserControllerTest` | 14 | multipart da foto, campos obrigatórios, resposta sem dados sensíveis |
| `controller/ApiExceptionHandlerTest` | 10 | 400/401/404/409 e exceção **sem mensagem** |

Nesses testes os filtros ficam **desligados** (`@AutoConfigureMockMvc(addFilters = false)`) e a
identidade do requisitante é injetada como atributo de requisição, exatamente como o `AuthFilter` faz
em produção:

```java
mockMvc.perform(get("/api/v1/transactions/{id}", id)
        .requestAttr("authenticatedUserId", ana.getIdUser()))
    .andExpect(status().isOk());
```

A cadeia de segurança tem testes próprios, com os filtros ligados — ver abaixo.

### 4.3 Segurança — filtros ligados

| Classe | Testes | O que cobre |
|---|---:|---|
| `config/SecurityChainTest` | 18 | sem header, header malformado, token adulterado/expirado/de usuário deletado, rota pública vs. protegida, preflight de CORS, política stateless |

Aqui o `SecurityConfig` é importado explicitamente:

```java
@WebMvcTest(controllers = AccountController.class)
@Import({SecurityConfig.class, AuthFilter.class, WebConfig.class})
```

Sem esse `@Import`, o `@WebMvcTest` aplica a segurança **padrão do Spring Boot** em vez da do projeto
— e o teste passa a verificar o Boot, não o NexusFinance. Foi o primeiro erro que apareceu ao escrever
esta classe: tudo respondia 401, e o cadastro respondia 403 por CSRF.

### 4.4 Arquitetura — invariantes (ArchUnit)

| Classe | Testes | O que cobre |
|---|---:|---|
| `architecture/ControllerArchitectureTest` | 7 | id na URL exige usuário autenticado, camadas, sem injeção por campo, sem `System.out` |

A regra principal:

> Todo método público de `@RestController` que recebe `@PathVariable UUID` tem que receber também
> `@RequestAttribute UUID authenticatedUserId`.

Essa regra não testa um caso — testa um **invariante**. Um teste de caso impede que *aquele* bug
volte; este impede que a *classe* de bug volte, inclusive em endpoints que ainda não existem. Ela foi
derivada do IDOR encontrado em `DELETE /api/v1/transactions/{id}`, e aponta o método exato quando
falha:

```
TransactionController.delete recebe @PathVariable UUID mas nao recebe
@RequestAttribute UUID authenticatedUserId
```

As regras usam a **API core** do ArchUnit em `@Test` comum, não o engine `archunit-junit5`: o projeto
está em JUnit Jupiter 6, e aquele engine foi construído para o JUnit 5.

### 4.5 Integração — MySQL real (`*IT`)

| Classe | O que cobre |
|---|---|
| `integration/AuthorizationMatrixIT` | a matriz: para **cada** endpoint com id na URL, usuário B não alcança recurso de A |
| `integration/TransactionRepositoryIT` | as 5 JPQL escritas à mão, com foco nos filtros opcionais de `searchByAccount` |
| `integration/DatabaseConstraintsIT` | os `CHECK` da V3, as `UNIQUE`, e as FKs `ON DELETE RESTRICT`/`CASCADE` |
| `integration/MonthlyForecastConcurrencyIT` | `@Version` e `LockModeType.PESSIMISTIC_WRITE` sob threads concorrentes |
| `integration/UserRegistrationIT` | cadastro e login de ponta a ponta, e o `save()` virando `merge` |
| `integration/ApplicationContextIT` | o contexto sobe, as migrations aplicam, o seeder fica fora do perfil de teste |

Por que MySQL real e não H2: as migrations usam `BINARY(16)`, `ENUM`, `MEDIUMBLOB`, `ENGINE=InnoDB` e
a collation `utf8mb4_0900_ai_ci`. Testar contra outro banco é testar outro sistema — e justamente os
bugs de FK e de constraint passariam.

Três coisas que só a integração pega:

- **JPQL não é verificada em tempo de compilação.** Um erro de sintaxe ou de nome de campo só aparece
  quando a query executa.
- **`save()` escolhe entre `persist` e `merge` pelo id ser nulo.** Nenhum mock reproduz isso, e era
  exatamente onde estava a vulnerabilidade de takeover de conta.
- **Chave estrangeira `ON DELETE RESTRICT` recusa o delete.** O mock de repositório aceita o delete sem
  reclamar; o banco não.

---

## 5. Fixtures

`src/test/java/com/dev/nexusfinance/fixtures/` monta objetos em memória:

```java
User ana = UserFixture.ana();        // dona dos recursos
User bruno = UserFixture.bruno();    // autenticado, dono de nada
Account conta = FinanceFixture.conta(ana);
List<Category> dicionario = FinanceFixture.dicionarioPadrao();
```

Dois usuários nomeados cobrem o caso mais frequente da suíte: verificar que um não acessa o recurso do
outro. `dicionarioPadrao()` já inclui as duas categorias de fallback (`Outros` e `Outras entradas`) que
o motor de categorização exige — sem elas ele lança `IllegalStateException`.

`UserFixture` hasheia a senha padrão **uma vez por JVM**. PBKDF2 com 210.000 iterações custa ~100 ms;
hashear por usuário criado deixaria a suíte visivelmente mais lenta.

`integration/TestDataFactory` é o equivalente para integração, mas **persiste** — os `*IT` precisam de
linhas de verdade para exercitar JPQL e constraints:

```java
User ana = dados.usuario("Ana Silva", "ana@email.com", "12345678901");
Account conta = dados.conta(ana, "Banco da Ana");
dados.transacao(conta, categoria, "IFOOD", "89.90", LocalDate.now(), TransactionSource.CSV);
```

Chame `dados.limparTudo()` no `@BeforeEach`: o container é compartilhado entre as classes de IT, então
o estado de uma não pode vazar para a próxima.

---

## 6. Perfil de teste

`src/test/resources/application-test.properties`:

- **sem datasource** por padrão — um teste que dependa de banco sem declarar o container falha de forma
  óbvia, em vez de silenciosamente acertar o MySQL de desenvolvimento;
- **Flyway desligado**; os `*IT` reabilitam explicitamente;
- **segredo de autenticação fixo**, para que os tokens gerados em teste sejam reproduzíveis;
- log em `WARN`, para a saída do teste ser legível.

O `CategorySeeder` tem `@Profile("!test")`. Ele é um `CommandLineRunner` e, sem essa guarda, gravava 6
categorias em **todo** start de contexto, inclusive em teste — o que daria aos testes de integração um
estado inicial implícito e dependente de ordem de execução.

---

## 7. Cobertura e o gate

O `jacoco:check` roda na fase `verify` e **quebra o build** se a cobertura cair abaixo do mínimo
configurado no `pom.xml`:

```xml
<jacoco.line.minimum>0.80</jacoco.line.minimum>
<jacoco.branch.minimum>0.70</jacoco.branch.minimum>
```

A política é **ratchet**: o mínimo sobe conforme a suíte cresce e nunca desce. Ele fica alguns pontos
abaixo da cobertura atingida, para o build não quebrar por flutuação — mas também não deixar a
cobertura voltar.

**Exclusões**, declaradas no `pom.xml`:

- `com/dev/nexusfinance/models/**` — getters e setters gerados pelo Lombok;
- `NexusFinanceApplication.class` — a classe `main`.

Código gerado e o `main` inflam o percentual sem informar nada sobre qualidade. A exclusão está
documentada de propósito: um número de cobertura só vale se você souber o que ele conta.

---

## 8. Receitas — escrevendo um teste novo

### Regra de negócio num service

Teste unitário puro, sem Spring. Mocks nas dependências, `Clock.fixed` se houver tempo envolvido.

```java
class MeuServiceTest {
    private MeuRepository repo;
    private MeuService service;

    @BeforeEach
    void setUp() {
        repo = mock(MeuRepository.class);
        service = new MeuService(repo, Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC));
    }
}
```

### Endpoint novo

Dois testes, em lugares diferentes:

1. **Contrato HTTP** em `controller/`, com `@WebMvcTest` e `addFilters = false`.
2. **Se o endpoint recebe id de recurso**, acrescente a linha no enum `EndpointComId` de
   `AuthorizationMatrixIT`. A matriz então cobre o novo endpoint em quatro dimensões de uma vez:
   acesso cruzado, sem token, token falsificado e acesso do dono.

O `ControllerArchitectureTest` já vai reclamar automaticamente se o método receber
`@PathVariable UUID` sem receber o usuário autenticado.

### Query de repositório

Teste de integração (`*IT`), estendendo `AbstractIntegrationIT`. JPQL só se verifica executando.

### Migration nova

`DatabaseConstraintsIT.migrationsRegistradas` lista as versões aplicadas — atualize-a. E se a migration
adiciona `CHECK` ou `UNIQUE`, escreva o teste que tenta violar a constraint via `JdbcTemplate`: o
objetivo é confirmar que ela existe **no schema**, não apenas no arquivo `.sql`.

---

## 9. Problemas comuns

**`Could not find a valid Docker environment`**
Docker parado. Abra o Docker Desktop e espere o daemon. Só afeta os `*IT`; `./mvnw test` continua
funcionando.

**`No tests matching pattern "ATest+BTest"`**
O separador de `-Dtest` é vírgula, não `+`.

**O `-Dtest` não acha meu teste de integração**
O Failsafe usa `-Dit.test`, não `-Dtest`.

**`./mvnw test` não gerou relatório de cobertura**
O `jacoco:report` está ligado à fase `verify`. Use `./mvnw test jacoco:report`.

**Um `@WebMvcTest` com filtros ligados devolve 401 em tudo**
Falta `@Import(SecurityConfig.class)`. Sem ele o Boot aplica a segurança padrão, não a do projeto.

**`@MethodSource` com campos nulos**
O provedor de argumentos é resolvido **antes** do `@BeforeEach`. Se os dados do teste nascem no
`@BeforeEach`, use `@EnumSource` (ou `@CsvSource`) com uma chave, e monte o valor dentro do método —
é o que `AuthorizationMatrixIT` faz.

**Jackson não compila: `com.fasterxml.jackson.databind` não existe**
O Spring Boot 4 usa Jackson 3. O `ObjectMapper` está em `tools.jackson.databind`. As *annotations*
seguem em `com.fasterxml.jackson.annotation`.

---

## 10. Documentos relacionados

- [../docs/PLANO_TESTES_E_QUALIDADE.md](../docs/PLANO_TESTES_E_QUALIDADE.md) — o diagnóstico e o plano
  que originaram esta suíte
- [../docs/ANALISE_SEGURANCA_E_EVOLUCAO.md](../docs/ANALISE_SEGURANCA_E_EVOLUCAO.md) — auditoria de
  segurança, qualidade e produto
