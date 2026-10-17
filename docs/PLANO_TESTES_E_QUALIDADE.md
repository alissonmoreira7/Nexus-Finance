# Plano de Testes e Qualidade — Back-end NexusFinance

> Documento complementar a [ANALISE_SEGURANCA_E_EVOLUCAO.md](ANALISE_SEGURANCA_E_EVOLUCAO.md).
> Enquanto aquele documento olha segurança e produto, este olha **a malha de testes**: o que ela cobre,
> o que ela deixa passar, e em que ordem fechar os buracos.
>
> Baseline medido em **2026-10-17** · Spring Boot 4.1.0 · Java 17 (runtime 25) · JUnit Jupiter 6.0.3
>
> ## ✅ Plano executado em 2026-10-17
>
> Os quatro sprints foram implementados. O resultado medido está em
> [RELATORIO_COBERTURA.md](RELATORIO_COBERTURA.md). As seções abaixo ficam como registro do
> diagnóstico que originou o trabalho — os números da §1 são o **baseline de partida**, não o estado atual.

---

## 1. Baseline medido (não estimado)

A primeira constatação de QA é que **não havia medição**. Não existe JaCoCo no `pom.xml`, então até
hoje a cobertura era desconhecida. Rodei o agente via CLI, sem alterar o projeto:

```bash
cd back-end
./mvnw -B org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test \
         org.jacoco:jacoco-maven-plugin:0.8.13:report
# relatório: back-end/target/site/jacoco/index.html
```

**Resultado: 41,2% de linha · 29,3% de branch** (213/517 linhas, 75/256 branches, 16 testes, 5 arquivos).

| Pacote | Linha | Branch | Linhas descobertas |
|---|---:|---:|---:|
| `controller` | **0,0%** | **0,0%** | 83 |
| `config` | **0,0%** | **0,0%** | 75 |
| `services` | 59,5% | 34,9% | 126 |
| `models` | 57,9% | 0,0% | 16 |
| `dto` | 80,0% | 16,7% | 1 |
| **TOTAL** | **41,2%** | **29,3%** | **304** |

Por classe, ordenado por risco (branches descobertos — onde as decisões moram):

| Classe | Linha | Branch | Branches descobertos |
|---|---:|---:|---:|
| `UserService` | 57,3% | 28,8% | **57** |
| `TransactionEngineService` | 45,8% | 34,6% | **34** |
| `AuthFilter` | 0,0% | 0,0% | **16** |
| `CategoryService` | 0,0% | 0,0% | 12 |
| `MonthlyForecastService` | 74,0% | 63,3% | 11 |
| `AccountService` | 0,0% | 0,0% | 8 |
| `TransactionController` | 0,0% | 0,0% | 8 |
| `UserController` | 0,0% | 0,0% | 6 |
| `AnalyticsService` | 0,0% | 0,0% | 4 |
| `SecurityConfig` | 0,0% | — | 26 linhas |

**Leitura:** a cobertura de linha (41%) não é terrível para um projeto de faculdade. A de **branch (29%)**
é o número que importa — significa que 7 de cada 10 decisões do código nunca foram exercidas. E o
desenho é de **pirâmide truncada**: existe só a base (unitário de service) e nada acima.

```
        /\            ← E2E / autorização ....... 0 testes
       /  \           ← Integração (DB real) .... 0 testes
      /----\          ← Slice de controller ..... 0 testes
     /______\         ← Unitário de service .... 16 testes  ← tudo está aqui
```

---

## 2. Pontos de melhoria de QA

### Grupo A — Infraestrutura de qualidade (o que falta antes de escrever teste)

**QA-01 · Sem medição de cobertura** · *Impacto alto, esforço trivial*
Sem JaCoCo no build, "aumentar a cobertura" não é verificável. É o primeiro item por definição: numa
disciplina de qualidade, a métrica precede a meta.

**QA-02 · Sem gate de qualidade no build**
Nada impede um PR baixar a cobertura. O `jacoco:check` com regra de mínimo transforma a meta em
contrato executável — e o padrão **ratchet** (o mínimo sobe a cada sprint, nunca desce) evita o
anti-padrão de "meta de 80% que ninguém alcança".

**QA-03 · Sem separação entre teste unitário e de integração**
Hoje tudo é `*Test` no Surefire. Quando entrarem testes com Testcontainers (segundos a dezenas de
segundos), eles vão deixar o feedback local lento e desincentivar rodar testes. Solução: convenção
`*Test` → Surefire (rápido, `mvn test`) e `*IT` → Failsafe (lento, `mvn verify`).

**QA-04 · Sem perfil de teste**
Não existe `src/test/resources/application-test.properties`. Qualquer `@SpringBootTest` hoje tentaria
subir no MySQL de desenvolvimento (`localhost:3307`) — testes que dependem da máquina do dev não são
testes, são rituais.

**QA-05 · `CategorySeeder` bloqueia teste de contexto** · *Bloqueador técnico*
É um `CommandLineRunner` sem guarda de perfil: roda em **todo** start de contexto, inclusive em teste,
escrevendo 6 categorias no banco. Precisa de `@Profile("!test")` antes de qualquer `@SpringBootTest` —
ou, melhor, virar um `@Bean` condicional. Sem isso, os testes de integração herdam estado implícito.

**QA-06 · Sem CI** · *não existe `.github/workflows/`*
Os testes só rodam quando alguém lembra. Um workflow mínimo (`mvn verify` + publicação do relatório
JaCoCo) é o que faz a malha de testes ter efeito sobre o processo, não só sobre o código.

**QA-07 · Sem análise da qualidade *dos testes* (mutação)**
Cobertura mede o que o teste *executa*, não o que ele *verifica*. Um teste sem `assert` dá 100% de
cobertura. **PIT (mutation testing)** mede o que o teste realmente detecta — e é exatamente o
argumento de uma disciplina de testes. Vale como diferencial de entrega.

**QA-08 · ArchUnit adicionado e não usado**
A dependência `archunit-junit5:1.3.0` está no `pom.xml` (ainda não commitada) e
`src/test/java/.../architecture/` está vazio. A regra mais valiosa já está escrita na auditoria
(§S-01): *"todo método de controller com `@PathVariable UUID` deve receber `@RequestAttribute UUID
authenticatedUserId`"*. Isso é um teste que **impede a classe de bug voltar** — vale mais que dez
testes de caso.
⚠️ *Atenção:* o projeto usa **JUnit Jupiter 6.0.3**; o engine `archunit-junit5` 1.3.0 foi feito para
JUnit 5. Escreva as regras como `@Test` comum usando a API core (`ArchRuleDefinition.methods()...`),
que é independente do engine, e troque a dependência para `com.tngtech.archunit:archunit`.

### Grupo B — Camadas de teste ausentes

**QA-09 · Zero testes de controller (`@WebMvcTest`)** · *83 linhas, 16 branches a 0%*
Nenhum contrato HTTP é verificado: nem status code, nem shape do JSON, nem desserialização. Toda a
validação de paginação de `TransactionController` (`page < 0 || size < 1 || size > 100`), todo o
`ApiExceptionHandler`, todo o mapeamento de `view()` está sem rede.

**QA-10 · Zero testes de segurança (`AuthFilter` + `SecurityConfig`)** · *105 linhas a 0%*
A camada que decide **quem entra** é a menos testada do projeto. Não há teste para: requisição sem
header, header malformado (`Basic`, `Bearer ` vazio), token expirado, token de usuário já deletado,
rota pública vs. protegida. Em um sistema financeiro, essa é a inversão de prioridade mais custosa.

**QA-11 · Zero testes de integração com banco**
As queries JPQL customizadas de `TransactionRepository` (5 delas, incluindo a `searchByAccount` com
três filtros opcionais e `:source is null`) nunca foram executadas em teste. JPQL quebra em
*runtime*, não em compilação. As 4 migrations Flyway e o `ddl-auto=validate` também nunca foram
validados por CI — divergência entre entidade e schema só aparece em produção.

**QA-12 · Zero testes de autorização entre usuários (o de maior retorno)**
Não existe nenhum teste que verifique "usuário B não acessa recurso de A". É o teste que pegaria o
IDOR do §S-01 e o mass assignment do §S-02 da auditoria. Deve ser uma **matriz** que cobre todo
endpoint que recebe id, não um caso isolado.

### Grupo C — Código que resiste a teste (refatorar para testabilidade)

**QA-13 · `LocalDate.now()` / `Instant.now()` embutidos** · *acoplamento temporal*
- `AnalyticsService.getSummary` usa `LocalDate.now().withDayOfMonth(1)` → **impossível** testar virada
  de mês, ano bissexto, ou o bug §B-03 (saldo que "zera" no dia 1º) sem mexer no relógio da máquina.
- `AuthService` usa `Instant.now()` em `createToken`/`extractUserId` → o caminho de **token expirado**
  só é testável esperando 8 horas.

Correção: injetar `java.time.Clock` (`Clock.systemUTC()` como `@Bean`, `Clock.fixed(...)` no teste).
É a refatoração de testabilidade com melhor relação custo/benefício do projeto e destrava dois
cenários hoje inalcançáveis.

**QA-14 · Métodos longos e privados concentram a lógica**
`TransactionEngineService.processAndReturn` faz, num só método privado, validação de lote + validação
de item + limpeza + categorização + montagem de entidade + persistência. Para testar a validação é
preciso mockar repositório e conta. Extrair um validador puro (ou um record `ValidTransaction`, como
`MonthlyForecastService` já faz bem com `ValidExpense`) torna 34 branches testáveis sem mock algum.

**QA-15 · `UserService.updateProfile` com 7 parâmetros**
`(UUID, String, String, String, byte[], String, boolean)` — incluindo uma *flag* booleana que troca o
comportamento. Cada teste precisa repetir os 7 argumentos e a combinação de flags explode. Um
*parameter object* (`ProfileUpdate`) reduz o custo marginal de cada novo caso de teste.

**QA-16 · Serviços instanciados com dependências `null` nos testes**
`new TransactionEngineService(null, null, null)` e `new UserService(users, images, null)` funcionam
hoje por coincidência (o caminho testado não toca a dependência). Qualquer refatoração que use a
dependência vira `NullPointerException` em vez de falha legível. Use mocks, mesmo que não verificados.

### Grupo D — Qualidade dos testes existentes

**QA-17 · `NexusFinanceApplicationTests` não testa o contexto**
O nome e a localização dizem "smoke test de aplicação", mas o arquivo não tem `@SpringBootTest`: são
dois testes unitários soltos (`PasswordService` e `cleanDescription`) numa classe de nome enganoso.
**Consequência real: o contexto Spring nunca sobe em teste.** Erro de wiring, bean faltando, `@Value`
sem default — nada disso é detectado pelo build. Dois ajustes: mover os testes para
`PasswordServiceTest` / `TransactionEngineServiceTest` e transformar este arquivo no `contextLoads()`
de verdade.

**QA-18 · Sem testes parametrizados**
Toda a validação de `UserService` (CPF, e-mail, nome, tamanho, magic bytes de 3 formatos de imagem) é
tabela de entrada/saída — material de `@ParameterizedTest` + `@CsvSource`. Hoje `validate()` tem **57
branches descobertos**; escrito caso a caso seriam ~25 métodos, parametrizado são 3.

**QA-19 · AssertJ disponível e não usado** *(3.27.7, já vem nos starters de teste)*
Os testes usam `assertEquals`/`assertThrows` do JUnit. AssertJ dá mensagem de falha muito melhor em
coleções e `BigDecimal` (`isEqualByComparingTo` evita o clássico falso-negativo de `scale` com
`assertEquals`) e `assertThatThrownBy(...).hasMessage(...)` encadeado.

**QA-20 · Sem fixtures / Object Mother**
`user.setIdUser(UUID.randomUUID())` + setters aparece repetido em 4 arquivos. Um
`src/test/java/.../fixtures/` com builders (`UserFixture.ana()`, `TransactionFixture.ifood()`) reduz
ruído e torna cada teste legível pelo que ele *varia*, não pelo que ele monta.

**QA-21 · Um teste congela um bug como comportamento esperado**
`mantemInferenciaDeTipoParaImportacaoSemTipo` documenta como intencional a inferência de tipo por
keyword sem usar o sinal do valor — que é o bug §B-01 da auditoria (estorno importado como despesa).
Quando o bug for corrigido, **este teste precisa ser reescrito, não removido**: ele deve passar a
afirmar a nova precedência (coluna `tipo` → sinal do valor → keyword).

---

## 3. Bugs que a malha atual não detecta

Esses cinco achados são novos (não estão na auditoria anterior) e todos existem **porque** a camada
correspondente não tem teste. Servem como alvos concretos: escreva o teste, veja falhar, corrija.

### BUG-01 · `cleanDescription` devolve string vazia quando a descrição é toda ruído ✅ *confirmado*

A guarda `if (raw.isBlank()) return "SEM DESCRICAO"` protege a **entrada**, não a **saída**. Se todos
os tokens forem removidos pelos regexes, sobra `""`. Executado:

```
[COMPRA VISA*1234 UBER EATS SAO PAULO] -> [UBER EATS]     ✅ ok
[COMPRA VISA*1234 SAO PAULO]           -> []              ❌ vazio
[PAGAMENTO DEBITO 123456]              -> []              ❌ vazio
[PIX RECEBIDO]                         -> [RECEBIDO]      ⚠️  perdeu o "PIX"
```

`clean_description` é `NOT NULL` no banco, mas `""` satisfaz `NOT NULL` — então a transação é
**gravada com descrição vazia**, e o usuário vê uma linha em branco no extrato. O único teste atual de
`cleanDescription` usa justamente o caso que funciona.
→ **Correção:** validar a saída (`return clean.isBlank() ? "SEM DESCRICAO" : clean`).
→ **Teste:** `@ParameterizedTest` com a tabela acima.

### BUG-02 · `ApiExceptionHandler` lança `NullPointerException` se a exceção não tem mensagem ✅ *confirmado*

```java
return ResponseEntity.status(status).body(Map.of("timestamp", ..., "status", ..., "message", message));
```

`Map.of` **rejeita valores nulos** (confirmado: `NullPointerException`). Uma
`IllegalArgumentException` sem mensagem — Jackson e `UUID.fromString` produzem isso em alguns
caminhos — faz o *handler* explodir, e o cliente recebe **500 em vez do 400 pretendido**. O handler de
erro é o último lugar onde se quer um bug.
→ **Correção:** `HashMap` + mensagem default, ou `Objects.requireNonNullElse(message, "Requisição inválida")`.
→ **Teste:** `@WebMvcTest` com um controller stub que lança `new IllegalArgumentException()`.

### BUG-03 · `DELETE /api/v1/users/me` retorna 500 para qualquer usuário real

`UserService.delete` faz `userRepository.deleteById(id)`. A entidade `User` não tem nenhum
`@OneToMany`, e as FKs de `tb_accounts` e `tb_monthly_forecasts` para `tb_users` são **`ON DELETE
RESTRICT`**. Logo: todo usuário que já criou uma conta ou um planejamento não pode ser deletado — o
MySQL recusa, sobe `DataIntegrityViolationException`, e **não há handler para ela** no
`ApiExceptionHandler` → 500 com stack trace.
Só um teste de integração com banco real pega isso: `@DataJpaTest` com H2 ou `ddl-auto` não reproduz
as FKs das migrations. Relaciona-se com o requisito LGPD de exclusão de conta (§B-07 da auditoria).
→ **Teste:** `UserDeletionIT` — criar usuário + conta, tentar deletar, afirmar o comportamento decidido.

### BUG-04 · Categorização por `contains` gera falso positivo

`categorize` usa `upper.contains(keyword)`. A keyword `TAXI` (categoria Transporte) casa com
`TAXIDERMIA`; `PE` (Pernambuco) casaria dentro de palavras se não houvesse o `\b` do regex anterior.
Verificado: `cleanDescription("TAXIDERMIA MUSEU")` → `TAXIDERMIA MUSEU` → categorizado como
**Transporte**.
→ **Teste:** caso de regressão com `TAXIDERMIA`, mais a correção para casamento por limite de palavra.

### BUG-05 · Transação com valor zero é aceita

A validação é `input.amount().signum() < 0` — rejeita negativo, **aceita zero**. Uma linha de extrato
com R$ 0,00 entra na base e polui o analytics com uma categoria de valor nulo.
→ **Teste:** caso de borda `BigDecimal.ZERO` no lote de importação.

---

## 4. Plano de execução — 4 sprints

Cada sprint tem **meta numérica verificável** e *Definition of Done*. A ordem é deliberada: primeiro
medir, depois cobrir onde há risco (HTTP e segurança), depois banco, depois profundidade.

### Sprint 0 — Instrumentação · *~3h · destrava todo o resto*

Nenhum teste novo. Só a infraestrutura que torna o progresso mensurável.

1. JaCoCo no `pom.xml` com `prepare-agent` + `report`, excluindo `models/**` e `NexusFinanceApplication`
   (getters/setters do Lombok e a classe `main` inflam a métrica sem informar nada).
   *Baseline com exclusões: ~40% — praticamente idêntico, o que mostra que o número não estava inflado.*
2. `src/test/resources/application-test.properties` + perfil `test`.
3. `@Profile("!test")` no `CategorySeeder` *(QA-05 — bloqueador)*.
4. Split Surefire (`*Test`) / Failsafe (`*IT`) *(QA-03)*.
5. Consertar `NexusFinanceApplicationTests` *(QA-17)*: mover os 2 testes unitários para os arquivos
   certos e deixar a classe com `@SpringBootTest` + `contextLoads()`.
6. Pasta `src/test/java/.../fixtures/` com `UserFixture` e `AccountFixture` *(QA-20)*.
7. Workflow `.github/workflows/ci.yml` rodando `./mvnw -B verify` *(QA-06)*.

**DoD:** `./mvnw verify` gera relatório JaCoCo; o contexto Spring sobe em teste pela primeira vez; CI
verde no push.

### Sprint 1 — Camada HTTP e segurança · *meta: 58% linha / 45% branch*

O maior buraco (158 linhas a 0%) e o de maior risco.

| # | Arquivo | Tipo | Cobre |
|---|---|---|---|
| 1 | `AuthorizationMatrixIT` | `@SpringBootTest` + MockMvc | **QA-12** — matriz usuário A vs. B em *todo* endpoint com id |
| 2 | `TransactionControllerTest` | `@WebMvcTest` | paginação inválida, busca > 100 chars, 201/204, shape do JSON |
| 3 | `ApiExceptionHandlerTest` | `@WebMvcTest` | 400/401/404 + **BUG-02** (exceção sem mensagem) |
| 4 | `AuthFilterTest` | `@WebMvcTest` + security-test | sem header, `Basic`, `Bearer` vazio, token adulterado, usuário deletado |
| 5 | `SecurityConfigTest` | `@WebMvcTest` | rota pública vs. protegida, OPTIONS liberado, corpo JSON do 401 |
| 6 | `UserControllerTest` | `@WebMvcTest` | `POST /users` com `idUser` no corpo (**§S-02**), multipart de foto |
| 7 | `ControllerArchitectureTest` | ArchUnit core | **QA-08** — `@PathVariable UUID` exige `@RequestAttribute` |
| 8 | `AccountControllerTest`, `AnalyticsControllerTest`, `CategoryControllerTest`, `MonthlyForecastControllerTest` | `@WebMvcTest` | status codes, `parseMonth` inválido |

**DoD:** `controller` ≥ 85% linha; `config` ≥ 70%; o teste #1 **falha** enquanto o IDOR do §S-01 não
for corrigido (e isso é o resultado desejado — o teste documenta o bug antes da correção).

### Sprint 2 — Integração com banco real · *meta: 70% linha / 58% branch*

Testcontainers MySQL executando as **migrations Flyway de verdade** — valida schema, JPQL e constraints.

> Coordenadas corretas para este projeto (Boot 4.1 gerencia **Testcontainers 2.0.5**, onde os
> artefatos foram renomeados em relação à 1.x):
> `org.springframework.boot:spring-boot-testcontainers`,
> `org.testcontainers:testcontainers-mysql`, `org.testcontainers:testcontainers-junit-jupiter`
> — todos sem `<version>` (o BOM resolve). Imagem: `mysql:8.4` (as migrations usam
> `utf8mb4_0900_ai_ci`, que exige MySQL 8+).

| # | Arquivo | Cobre |
|---|---|---|
| 1 | `AbstractIntegrationIT` | container compartilhado via `@ServiceConnection` + `@Testcontainers` |
| 2 | `FlywayMigrationIT` | as 4 migrations aplicam limpo e `ddl-auto=validate` passa |
| 3 | `TransactionRepositoryIT` | as 5 JPQL, com foco em `searchByAccount` (combinações de `source`/`type`/`search` nulos e vazios) |
| 4 | `UserDeletionIT` | **BUG-03** — FK `RESTRICT` ao deletar usuário com conta |
| 5 | `MonthlyForecastConcurrencyIT` | `@Version` + `LockModeType.PESSIMISTIC_WRITE` sob 2 threads — a parte mais sofisticada do projeto, hoje não verificada |
| 6 | `UserCreationIT` | **§S-02** — `save()` virando `merge` com `idUser` preenchido |
| 7 | `DatabaseConstraintsIT` | os `CHECK` da V3 (`expected_income >= 0`, `amount > 0`) e a UNIQUE `uk_forecast_user_month` |

**DoD:** `./mvnw verify` sobe o container e passa; `FlywayMigrationIT` no CI impede migration quebrada
de entrar na main.

### Sprint 3 — Profundidade e qualidade dos testes · *meta: 80% linha / 70% branch*

| # | Alvo | Técnica | Branches a fechar |
|---|---|---|---|
| 1 | `UserService.validate` / `normalize*` / `validateImage` | `@ParameterizedTest` + `@CsvSource` *(QA-18)* | **57** |
| 2 | `TransactionEngineService` | extrair validador *(QA-14)* + parametrizado; **BUG-01**, **BUG-04**, **BUG-05** | **34** |
| 3 | `AnalyticsService` | injetar `Clock` *(QA-13)* → testar virada de mês; hoje 0% | 4 |
| 4 | `AuthService` | `Clock` → testar **token expirado**, hoje inalcançável | 7 |
| 5 | `AccountService`, `CategoryService` | unitário simples, hoje 0% | 20 |
| 6 | `PasswordService` | caminho de fallback não-pbkdf2 (comparação de texto puro — fixar e documentar) | 5 |
| 7 | Toda a suíte | migrar asserções para AssertJ *(QA-19)* | — |
| 8 | Toda a suíte | **PIT mutation testing** *(QA-07)* — meta: ≥ 60% de mutation score em `services` | — |

**DoD:** gate do JaCoCo em 80/70 ativo e verde; relatório PIT anexado à entrega.

---

## 5. Metas, por sprint

| Marco | Linha | Branch | Testes (aprox.) | Gate ativo |
|---|---:|---:|---:|---|
| **Hoje** | **41,2%** | **29,3%** | 16 | — |
| Sprint 0 | 41% | 29% | 17 | relatório, sem gate |
| Sprint 1 | 58% | 45% | ~60 | 55 / 40 |
| Sprint 2 | 70% | 58% | ~85 | 68 / 55 |
| Sprint 3 | 80% | 70% | ~130 | 80 / 70 + PIT 60% |

O gate sempre fica **alguns pontos abaixo** da meta alcançada (padrão *ratchet*): o build nunca
quebra por flutuação, mas também nunca deixa a cobertura voltar.

---

## 6. Como medir, a cada passo

```bash
cd back-end

./mvnw test                      # unitários — feedback rápido
./mvnw verify                    # unitários + integração (*IT) + gate do JaCoCo
./mvnw -Dtest=AuthServiceTest test

# relatório
start target/site/jacoco/index.html

# resumo por pacote direto do CSV (o que gerou as tabelas deste documento)
awk -F, 'NR>1 {lm[$2]+=$8; lc[$2]+=$9; bm[$2]+=$6; bc[$2]+=$7}
  END {for (p in lm) {l=lm[p]+lc[p]; b=bm[p]+bc[p];
  printf "%-45s %6.1f%% linha  %6.1f%% branch\n", p, 100*lc[p]/l, (b?100*bc[p]/b:0)}}' \
  target/site/jacoco/jacoco.csv | sort

# mutação (Sprint 3)
./mvnw org.pitest:pitest-maven:mutationCoverage
```

---

## 7. Ordem recomendada, em uma linha

**Sprint 0** (medir e destravar) → **`AuthorizationMatrixIT`** (o teste de maior retorno do projeto,
e que prova dois bugs críticos antes de corrigi-los) → resto do Sprint 1 → Sprint 2 → Sprint 3.

Se o tempo da disciplina apertar, o recorte mínimo defensável é **Sprint 0 + Sprint 1**: sai de 41%
para ~58% de linha, cobre a camada onde moram os bugs críticos, e entrega três artefatos que impressionam
em avaliação de qualidade — matriz de autorização, teste de arquitetura (ArchUnit) e gate de cobertura
no CI.
