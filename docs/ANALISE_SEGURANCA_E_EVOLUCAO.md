# Nexus Finance — Análise de Segurança, Qualidade e Evolução

> Revisão completa do repositório (back-end Spring Boot 4.1 + front-end React 19/Vite).
> Data: 2026-09-09 · Branch: `main` · Commit base: `defeb29`

Este documento tem quatro objetivos:

1. Listar os **problemas de segurança e qualidade** encontrados, com localização e correção sugerida.
2. Apontar **regras de negócio** que hoje estão frágeis ou incompletas.
3. Mapear o **roadmap de estudo** diretamente sobre os 6 temas do backlog
   (Pluggy, OAuth 2.0 + PKCE, Criptografia Ágil, Envelope DEK/KEK, FAPI/mTLS, PQC).
4. Propor **inovação de produto** ancorada em dores reais e nas regras de negócio que o sistema
   já modela — não em uma lista genérica de features de fintech.

---

## Sumário executivo

O projeto está **acima da média** para um projeto de estudo: há Flyway com migrations versionadas,
`ddl-auto=validate`, `open-in-view=false`, PBKDF2 com 210k iterações, comparação em tempo constante,
validação de magic bytes no upload de imagem, lock pessimista + `@Version` no planejamento mensal e
testes unitários cobrindo justamente os pontos sensíveis. Isso é sinal de cuidado real.

O que quebra esse padrão são **pontos isolados que ficaram para trás** da evolução do resto do código
— principalmente dois endpoints que não receberam a checagem de dono e um par de defaults de
configuração que viram falha crítica se o `.env` não existir em produção.

| Severidade | Qtd | Destaques |
|---|---|---|
| 🔴 Crítico | 3 | IDOR no delete de transação · mass assignment em `POST /users` · secret default no properties |
| 🟠 Alto | 5 | Fallback de senha em texto plano · categorias globais graváveis · token sem revogação · sem rate limit no login · CPF/dados financeiros sem cifra |
| 🟡 Médio | 9 | Sem Bean Validation · timing oracle no login · `DELETE /users/me` → 500 · sem auditoria · N+1 de requisições no dashboard · etc. |
| 🔵 Qualidade | 8 | Estilo inconsistente, imports FQN, nomes `snake_case` em Java, dead code do Google OAuth, ausência de testes de controller |

---

## 1. Segurança

### 🔴 CRÍTICO

#### S-01 · IDOR: qualquer usuário autenticado apaga a transação de qualquer outro

**Onde:** [TransactionController.java:49-53](back-end/src/main/java/com/dev/nexusfinance/controller/TransactionController.java#L49-L53) → [TransactionEngineService.java:40](back-end/src/main/java/com/dev/nexusfinance/services/TransactionEngineService.java#L40)

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable UUID id) {   // <- sem authenticatedUserId
    service.deleteTransaction(id);                            // <- deleteById direto
    return ResponseEntity.noContent().build();
}
```

Todos os outros endpoints de `TransactionController` chamam `accounts.assertOwnership(...)`.
Este ficou de fora — e ainda por cima o service faz `deleteById` sem carregar a entidade.
Com um token válido de qualquer conta, um atacante que descubra um UUID de transação apaga o dado de
outra pessoa. Não há soft delete nem auditoria, então **a perda é definitiva e invisível**.

**Correção:**

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@RequestAttribute UUID authenticatedUserId, @PathVariable UUID id) {
    Transaction transaction = service.findById(id);
    accounts.assertOwnership(transaction.getAccount().getIdAccount(), authenticatedUserId);
    service.deleteTransaction(id);
    return ResponseEntity.noContent().build();
}
```

E, no service, prefira `delete(entity)` sobre `deleteById(id)` para que a exclusão passe pelo mesmo
caminho de carregamento/validação.

> **Regra de ouro para o projeto:** nenhum `@PathVariable UUID` deve chegar num repositório sem antes
> passar por uma verificação de dono. Vale escrever um teste de arquitetura (ArchUnit) que falhe se um
> método de controller receber `@PathVariable UUID` e não receber `@RequestAttribute UUID authenticatedUserId`.

---

#### S-02 · Mass assignment em `POST /api/v1/users` → sobrescrita de usuário existente

**Onde:** [UserController.java:18-21](back-end/src/main/java/com/dev/nexusfinance/controller/UserController.java#L18-L21)

O endpoint é **público** (liberado no `SecurityConfig` e no `shouldNotFilter`) e recebe a **entidade JPA
`User` crua** via `@RequestBody`. A `User` tem `@Getter @Setter` do Lombok e nenhum `@JsonIgnore` no
`idUser`, então Jackson desserializa o campo. Em seguida:

```java
return userRepository.save(user);   // UserService.create
```

`SimpleJpaRepository.save()` decide entre `persist` e `merge` por `id == null`. Com `idUser` preenchido
pelo atacante, vira **`merge`** — ou seja, um `UPDATE` na linha daquele UUID:

```http
POST /api/v1/users
{ "idUser": "<uuid-da-vítima>", "name": "x", "cpf": "00000000191",
  "email": "atacante@mail.com", "password": "123456" }
```

Resultado: e-mail e senha da vítima são trocados pelos do atacante — **account takeover sem
autenticação**. As checagens de unicidade não protegem, porque o e-mail e o CPF enviados são novos.

Vale notar que `role` e `plan` **estão** protegidos (sobrescritos em `create`), o que mostra que a
preocupação existia; faltou o `id`.

**Correção (duas camadas, aplique as duas):**

1. **Nunca exponha entidade JPA no contrato HTTP.** Crie um `CreateUserRequest` record:
   ```java
   public record CreateUserRequest(String name, String cpf, String email, String password) {}
   ```
   E retorne `UserView`, não `User`. Isso resolve S-02 e previne toda uma família de bugs futuros.
2. **Defensivo no service:** `user.setIdUser(null)` antes do `save`, ou use `entityManager.persist()`
   explicitamente no fluxo de criação, que lança exceção se o id já existir.

---

#### S-03 · Secrets com default funcional em `application.properties`

**Onde:** [application.properties:4-10](back-end/src/main/resources/application.properties#L4-L10)

```properties
spring.datasource.username=${DB_USER:admin}
spring.datasource.password=${DB_PASSWORD:admin}
app.auth.secret=${APP_AUTH_SECRET:change-this-development-secret-with-at-least-32-characters}
```

O valor default do `app.auth.secret` está **versionado no Git e é público**. Se o `.env` não for
carregado em produção (container sem variável, typo no nome, deploy novo), a aplicação **sobe
normalmente** assinando tokens com um segredo que qualquer um lê neste repositório. Como o token é
apenas `base64(uuid:exp).HMAC`, forjar um token válido para **qualquer UUID de usuário** vira exercício
de 5 linhas. Falha silenciosa é o pior tipo de falha de segurança.

**Correção:**

```properties
app.auth.secret=${APP_AUTH_SECRET}
spring.datasource.password=${DB_PASSWORD}
```

Sem default, o Spring falha no boot (`Could not resolve placeholder`) — que é exatamente o comportamento
desejado. Para desenvolvimento, use um `application-dev.properties` com `@Profile("dev")` e mantenha um
`.env.example` documentado no README.

Adicione também uma validação no construtor do `AuthService`:

```java
if (secret.length() < 32) throw new IllegalStateException("APP_AUTH_SECRET deve ter ao menos 32 caracteres");
```

---

### 🟠 ALTO

#### S-04 · `PasswordService.matches` compara senha em texto plano como fallback

**Onde:** [PasswordService.java:24-30](back-end/src/main/java/com/dev/nexusfinance/services/PasswordService.java#L24-L30)

```java
if (encoded == null || !encoded.startsWith("pbkdf2$")) {
    return MessageDigest.isEqual(password.getBytes(...), encoded.getBytes(...));  // texto plano!
}
```

É um caminho de compatibilidade com hashes legados, mas na prática significa: **se alguém conseguir
gravar um valor não-`pbkdf2$` na coluna `password`, esse valor vira a senha em texto plano**. Combinado
com S-02 (que permite `merge` arbitrário), o risco se multiplica. E não há nenhum registro migrado que
justifique manter o fallback.

**Correção:** remova o branch. Se um hash não tem o prefixo esperado, retorne `false` e logue um alerta
de integridade — é um dado corrompido, não uma senha válida.

Aproveite para trocar a implementação artesanal pelo `PasswordEncoder` do Spring Security
(`DelegatingPasswordEncoder` com Argon2id como padrão). O formato `{argon2id}$argon2id$v=19$...` já
resolve versionamento de algoritmo nativamente — que é literalmente o assunto de **Criptografia Ágil**
no seu backlog (ver §4.3).

---

#### S-05 · Qualquer usuário autenticado escreve no dicionário global de categorias

**Onde:** [CategoryController.java:13](back-end/src/main/java/com/dev/nexusfinance/controller/CategoryController.java#L13)

`tb_categories` não tem `id_user` — é uma tabela **global compartilhada por todos**. O `POST /categories`
exige apenas autenticação. Um usuário pode cadastrar `Categoria "Lazer" / keywords: "SALARIO,ALUGUEL"` e
**envenenar a categorização de todos os outros usuários**, corrompendo relatórios e analytics alheios.

Além do risco, é uma limitação de produto: hoje ninguém consegue ter categoria própria.

**Correção (prefira a B):**

- **A — curto prazo:** exija `ROLE_ADMIN` no `POST /categories`. Exige criar a role (hoje `UserRole` só
  tem `USER`) e uma migration para o enum da coluna.
- **B — correto:** dê escopo ao dicionário. Adicione `id_user NULL` em `tb_categories`
  (`NULL` = categoria de sistema, preenchido = categoria do usuário), e faça
  `TransactionEngineService.categorize` consultar `categorias do usuário ∪ categorias de sistema`, com a
  do usuário tendo precedência. Isso também abre a porta para regras personalizadas (§3.6).

---

#### S-06 · Token opaco sem revogação, sem `jti`, sem rotação e em `localStorage`

**Onde:** [AuthService.java:86-93](back-end/src/main/java/com/dev/nexusfinance/services/AuthService.java#L86-L93) · [api.ts:8](front-end/src/services/api.ts#L8)

O token é `base64url(uuid:exp) + "." + HMAC-SHA256`. A construção em si é sólida (HMAC correto,
comparação em tempo constante, TTL de 8h) — mas o modelo tem limitações importantes:

| Problema | Consequência |
|---|---|
| Sem identificador (`jti`) e sem lista de revogação | **Logout é só client-side.** Um token roubado vale 8h inteiras, mesmo depois do "Sair da conta" |
| Sem `iat`/`nbf`, sem versão de chave (`kid`) | Rotacionar `APP_AUTH_SECRET` desloga 100% da base de uma vez; impossível rotação gradual |
| Sem refresh token | Obriga TTL longo (8h) para não irritar o usuário — o oposto do desejável |
| Guardado em `localStorage` | Qualquer XSS exfiltra a sessão. Cookie `httpOnly` não teria esse problema |
| Sem *binding* ao cliente (mTLS, DPoP) | Token é *bearer* puro: quem tem, é |

**Correção incremental:**

1. Adicione `jti` (UUID) ao payload e uma tabela `tb_revoked_tokens` (ou Redis com TTL = exp).
   `POST /auth/logout` insere o `jti`; o `AuthFilter` consulta.
2. Adicione `kid` ao payload e mantenha um mapa de chaves ativas → rotação sem deslogar ninguém.
3. Reduza o access token para 15 min e adicione refresh token rotativo (`httpOnly`, `SameSite=Strict`,
   `Secure`), com detecção de reuso.
4. Junto do passo 3, mova o access token para memória (React state) e o refresh para o cookie —
   mitiga XSS de verdade.

> Este item é a ponte natural para o estudo de **OAuth 2.0 + PKCE** (§4.2). Não invente um servidor de
> autorização: quando chegar lá, adote o Spring Authorization Server.

---

#### S-07 · Sem rate limiting nem proteção contra força bruta no login

**Onde:** [AuthController.java:14-17](back-end/src/main/java/com/dev/nexusfinance/controller/AuthController.java#L14-L17)

Não há throttling, lockout, CAPTCHA nem backoff. A senha mínima é de **6 caracteres**
([UserService.java:117](back-end/src/main/java/com/dev/nexusfinance/services/UserService.java#L117)) e não
há checagem contra listas de senhas vazadas. Um atacante testa senhas comuns contra e-mails conhecidos
sem nenhum atrito. Os 210k rounds de PBKDF2 ajudam (limitam a taxa), mas também viram um **vetor de DoS**:
cada tentativa custa ~100ms de CPU do servidor.

**Correção:**

- Rate limit por IP **e** por conta-alvo (Bucket4j é a escolha idiomática em Spring).
- Lockout exponencial após N falhas na mesma conta, com desbloqueio por e-mail.
- Suba o mínimo para 12 caracteres, ou (melhor) adote a política do NIST SP 800-63B: mínimo 8 +
  verificação contra o dataset do HaveIBeenPwned (API k-anonymity, não envia a senha).
- Registre tentativas falhas no log de auditoria (S-11).

---

#### S-08 · Dados financeiros e CPF em claro no banco

**Onde:** [V1__create_initial_schema.sql](back-end/src/main/resources/db/migration/V1__create_initial_schema.sql) · [User.java](back-end/src/main/java/com/dev/nexusfinance/models/User.java)

`cpf`, `name`, `email`, `raw_description`, `clean_description` e `amount` estão todos em texto puro.
Um dump do MySQL (backup vazado, credencial exposta, SQL injection em código futuro) entrega o extrato
bancário completo e o CPF de todos os usuários. Para um app que se apresenta como **Open Finance**, é o
gap mais relevante frente ao que a LGPD e o Bacen esperam.

**Correção — e é exatamente aqui que o estudo de DEK/KEK se aplica** (detalhes em §4.1):

- **CPF:** cifre com AES-256-GCM usando uma DEK por usuário, e guarde **também** um índice cego
  (`HMAC-SHA256(cpf, pepper)`) numa coluna separada para permitir o `findByCpf` sem decifrar nada.
- **`raw_description` / `clean_description`:** cifradas com a DEK do usuário.
- **`amount`:** mantenha em claro por enquanto — cifrar quebra os `SUM()` do Analytics. (Cifra
  homomórfica ou agregação pré-computada é assunto para depois; não force.)
- **E-mail:** índice cego + valor cifrado, mesmo padrão do CPF.

---

### 🟡 MÉDIO

#### S-09 · Timing oracle permite enumeração de usuários no login

**Onde:** [AuthService.java:38-49](back-end/src/main/java/com/dev/nexusfinance/services/AuthService.java#L38-L49)

A mensagem de erro é corretamente genérica ("Credenciais inválidas") — mas o **tempo de resposta não é**.
Se o e-mail não existe, o método lança antes de chegar em `passwords.matches()`, pulando os ~100ms de
PBKDF2. A diferença é grande o suficiente para medir por HTTP: **e-mail existente ≈ 100ms, inexistente ≈ 2ms**.

**Correção:** sempre execute o KDF, mesmo sem usuário, contra um hash dummy pré-computado:

```java
private static final String DUMMY_HASH = /* hash de uma senha aleatória, computado no boot */;

User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
String encoded = user == null ? DUMMY_HASH : user.getPassword();
boolean ok = passwords.matches(password, encoded);
if (user == null || !ok) throw new UnauthorizedException("Credenciais inválidas");
```

---

#### S-10 · `DELETE /api/v1/users/me` retorna 500 quando o usuário tem conta

**Onde:** [UserService.java:98-101](back-end/src/main/java/com/dev/nexusfinance/services/UserService.java#L98-L101)

`tb_accounts.fk_accounts_user` é `ON DELETE RESTRICT`, e o registro de conta é criado automaticamente no
cadastro ([AuthContext.tsx](front-end/src/contexts/AuthContext.tsx) chama `POST /accounts` logo após o
registro). Ou seja, **todo** usuário tem pelo menos uma conta, e `userRepository.deleteById(id)` sempre
estoura `DataIntegrityViolationException` → 500, sem tratamento no `ApiExceptionHandler`.

Não há UI que chame esse endpoint hoje, mas ele está exposto e funcional.

**Correção:** decida a regra de negócio primeiro (ver B-07 e §5 — exclusão de conta é requisito LGPD). No
mínimo, implemente exclusão em cascata transacional (transações → contas → previsões → imagem → usuário)
ou soft delete com anonimização. E adicione um `@ExceptionHandler(DataIntegrityViolationException.class)`
retornando 409 Conflict.

---

#### S-11 · Ausência total de logging e trilha de auditoria

Não há um único `Logger` em `back-end/src/main`. Para uma aplicação financeira isso significa:
não é possível investigar um incidente, detectar força bruta, provar quem apagou uma transação
(especialmente relevante dado o S-01) ou atender a um pedido de auditoria.

**Correção:** log estruturado (JSON) com um `correlationId` por request, e uma tabela `tb_audit_log`
para eventos sensíveis: login (sucesso/falha), logout, alteração de perfil, criação/exclusão de
transação, importação de lote, alteração de renda prevista. Nunca logue senha, token, CPF completo
ou valores — logue ids e tipos de evento.

---

#### S-12 · Sem Bean Validation — validação manual e inconsistente

`spring-boot-starter-validation` não está no [pom.xml](back-end/pom.xml) e não há um `@Valid` no projeto.
Toda validação é `if` manual dentro dos services. Funciona, mas:

- Espalha regra por vários lugares (`validate`, `normalizeEmail`, `normalizeCpf`, `money`, `validateExpense`).
- Não valida o **formato do CPF** de fato — `00000000000` e `11111111111` passam
  ([UserService.java:115](back-end/src/main/java/com/dev/nexusfinance/services/UserService.java#L115) só
  checa `\d{11}`). Para um app que usa CPF como identidade, valide os dígitos verificadores.
- O regex de e-mail (`^[^@\s]+@[^@\s]+\.[^@\s]+$`) aceita coisas inválidas e rejeita válidas.

**Correção:** adicione a dependência, anote os DTOs (`@NotBlank`, `@Email`, `@Size`, `@Positive`,
`@PastOrPresent`) e use o `org.hibernate.validator.constraints.br.CPF`, que já vem no Hibernate Validator
e faz os dígitos verificadores. Mantenha as regras de domínio complexas nos services.

---

#### S-13 · Sem limite de tamanho de payload no upload de lote

**Onde:** [TransactionController.java:23](back-end/src/main/java/com/dev/nexusfinance/controller/TransactionController.java#L23)

O limite de 10.000 transações é validado **depois** do parse completo do JSON. Um payload de 500 MB é
totalmente desserializado em memória antes da checagem. `spring.servlet.multipart.max-request-size=2MB`
só cobre multipart, e este endpoint é `application/json`.

**Correção:** configure limites no Tomcat e um filtro de `Content-Length` no endpoint. Melhor ainda:
mude o contrato para **upload multipart do CSV** e processe em streaming no servidor (ver B-02), o que
também tira do front-end a responsabilidade de parsear.

---

#### S-14 · Foto de perfil viaja em base64 dentro de `/users/me`

**Onde:** [UserView.java:11-16](back-end/src/main/java/com/dev/nexusfinance/dto/UserView.java#L11-L16)

Toda restauração de sessão (`AuthContext` no boot do app) baixa até ~1,33 MB de base64. Não há
cache HTTP, ETag nem `Cache-Control`, e o payload ainda vai parar no `localStorage` via
`localStorage.setItem('user', JSON.stringify(...))` — que tem cota de ~5 MB por origem.

**Correção:** endpoint dedicado `GET /api/v1/users/me/photo` retornando os bytes com `Content-Type`
correto, `ETag` (hash do conteúdo) e `Cache-Control: private, max-age=86400`. O `UserView` passa a
devolver só uma URL. A médio prazo, mova o binário para object storage (S3/MinIO) com URL assinada.

---

#### S-15 · CORS e cabeçalhos de segurança

[WebConfig.java](back-end/src/main/java/com/dev/nexusfinance/config/WebConfig.java) está **correto** —
origem única, sem `allowCredentials`, métodos e headers explícitos. Bom trabalho. O que falta:

- Nenhum header de segurança além dos defaults do Spring Security. Falta CSP,
  `Strict-Transport-Security`, `Referrer-Policy: no-referrer`, `Permissions-Policy`.
- Não há redirect/exigência de HTTPS.
- O front-end não define CSP no `index.html`. Com token em `localStorage` (S-06), CSP é a principal
  defesa em profundidade contra XSS.

---

#### S-16 · `ApiExceptionHandler` não cobre os casos que geram 500

**Onde:** [ApiExceptionHandler.java](back-end/src/main/java/com/dev/nexusfinance/controller/ApiExceptionHandler.java)

Faltam handlers para `DataIntegrityViolationException` (→ 409), `MethodArgumentTypeMismatchException`
(→ 400 quando o UUID da URL é malformado), `HttpMessageNotReadableException` (→ 400 em JSON inválido) e
um `Exception.class` genérico que devolva mensagem neutra + `correlationId`, sem vazar detalhe de
implementação. Hoje, um `IllegalStateException` do `TransactionEngineService` ("Cadastre uma categoria
chamada Outros") vaza direto como 500.

---

#### S-17 · Google OAuth configurado mas não funcional (dead code enganoso)

**Onde:** [main.tsx:25](front-end/src/main.tsx#L25) · [Login.tsx:16-22](front-end/src/pages/Login/Login.tsx#L16-L22)

O `clientId` está **hardcoded no fonte** (é público por natureza, então não é vazamento de segredo — mas
deveria ser `VITE_GOOGLE_CLIENT_ID`), e o `onSuccess` só exibe "ainda não está disponível". O usuário
passa pelo consentimento do Google, o app recebe o token e joga fora. É um botão que promete
autenticação e não entrega.

**Correção:** ou remova o botão e a dependência `@react-oauth/google` até implementar, ou implemente de
verdade — e essa é a porta de entrada para o estudo de **OAuth 2.0 + PKCE** (§4.2).

---

## 2. Qualidade de código e arquitetura

### Q-01 · Estilo inconsistente entre os "dois códigos" do projeto

Existem claramente duas gerações de código no repositório:

**Código maduro** — `MonthlyForecastService`, `UserService`, `AuthService`, `PasswordService`: uma
declaração por linha, nomes descritivos, validação centralizada, records para DTO, testes.

**Código apressado** — `AccountController`, `CategoryController`, `AnalyticsController`, `CategorySeeder`,
`TransactionEngineService`: múltiplos statements por linha, métodos inteiros em uma linha só, imports
fully-qualified no meio do código (`com.dev.nexusfinance.services.AccountService accounts`).

```java
// AccountController.java:19 — três responsabilidades numa linha
@DeleteMapping("/{id}") public ResponseEntity<Void> delete(@RequestAttribute UUID authenticatedUserId, @PathVariable UUID id) { service.assertOwnership(id, authenticatedUserId); service.delete(id); return ResponseEntity.noContent().build(); }
```

Não é preciosismo: **foi justamente na classe mais comprimida (`TransactionController`) que o IDOR do
S-01 passou despercebido**. Linhas densas escondem o que falta nelas.

**Correção:** adote Spotless + google-java-format no build (`mvn spotless:apply`), com `mvn verify`
falhando se o formato divergir. Um comando, resolve para sempre.

### Q-02 · Nomenclatura `snake_case` em campos Java

**Onde:** [Transaction.java](back-end/src/main/java/com/dev/nexusfinance/models/Transaction.java)

`raw_description`, `clean_description`, `transaction_date` violam a convenção Java e forçam getters
estranhos (`getClean_description()`, `getTransaction_date()`) espalhados por controllers e queries JPQL.

**Correção:** renomeie para `rawDescription`, `cleanDescription`, `transactionDate`. O `@Column(name=...)`
já mapeia para as colunas do banco, então **não precisa de migration** — é refactor puro de código.
Atualize as JPQL do `TransactionRepository` junto.

### Q-03 · Entidades JPA expostas na API

`POST /users` recebe e devolve `User`; `CategoryController` recebe e devolve `Category`. Além do S-02,
isso acopla o contrato HTTP ao schema do banco: qualquer campo novo na entidade vaza automaticamente
para a API, e qualquer renomeação quebra os clientes. `UserView` mostra que o padrão certo já é conhecido
no projeto — só não foi aplicado em todo lugar.

### Q-04 · `@Transactional` ausente onde importa

- `TransactionEngineService.deleteTransaction` — sem `@Transactional`.
- `AccountService` — **nenhum** método transacional, incluindo `create` (que faz find + save).
- `UserService.create` — corretamente `@Transactional`, mas com um TOCTOU: a checagem
  `findByCpf`/`existsByEmail` e o `save` não são atômicos sob concorrência. A constraint UNIQUE do banco
  salva, mas o erro sai como 500 em vez de 400. Trate `DataIntegrityViolationException`.

### Q-05 · N+1 de requisições HTTP no Dashboard

**Onde:** [Dashboard.tsx:44-52](front-end/src/pages/Dashboard/Dashboard.tsx#L44-L52) e `loadAccountTransactions`

O dashboard busca contas, depois dispara **1 request de analytics por conta**, depois **N requests de
transações por conta**, e faz merge, ordenação e paginação **no cliente**. Com 3 contas e página 3, são
~13 requisições HTTP para montar uma tela. E a paginação client-side está incorreta: somar
`totalElements` de contas diferentes e fatiar localmente produz páginas com contagem inconsistente.

O `Relatorios.tsx` tem um bug relacionado: ao agregar os summaries, ele mantém
`expensesByCategory: total.expensesByCategory` — ou seja, **descarta as categorias de todas as contas
exceto a primeira**.

**Correção:** crie endpoints agregados no back-end:
- `GET /api/v1/analytics/summary?month=YYYY-MM` — consolidado de todas as contas do usuário.
- `GET /api/v1/transactions?page=&size=&...` — extrato consolidado, paginado **no banco**.

Isso respeita o RNF01 (analytics resolvido em query no banco) e reduz a tela a 2 requisições.

### Q-06 · Cobertura de testes com um buraco específico

Os 4 arquivos de teste são bons e testam exatamente o que importa em regra de negócio (categorização por
tipo, precisão monetária, isolamento entre usuários no forecast, magic bytes de imagem, token adulterado).

O que falta é justamente a camada onde estão os bugs críticos:

- **Zero testes de controller / `@WebMvcTest`** — S-01 e S-02 seriam pegos por um teste que faz
  "usuário A tenta apagar transação de B → espera 401/403".
- Zero testes de integração com banco (`@DataJpaTest` + Testcontainers MySQL) — validariam as migrations
  e o comportamento de `save` vs `merge` do S-02.
- Zero testes no front-end (nenhum Vitest/Testing Library configurado). O `parseCsv` é lógica pura,
  cheia de casos de borda, e não tem um único teste.

**Correção prioritária:** um `AuthorizationIntegrationTest` que, para **cada** endpoint que recebe um id,
verifica que o usuário B não acessa o recurso de A. É o teste com melhor retorno neste projeto.

### Q-07 · Sem CI, sem análise estática, sem scan de dependências

Não há `.github/workflows/`. Um pipeline mínimo já pegaria vários itens acima:

```yaml
# .github/workflows/ci.yml
- mvn -B verify                                  # testes + spotless
- npm ci && npm run lint && npm run build
- mvn org.owasp:dependency-check-maven:check     # CVEs nas libs
```

Adicione Dependabot e, se quiser ir além, SpotBugs + o plugin `find-sec-bugs`.

### Q-08 · Pontos menores

- `LembrarUserButton.tsx` ("Lembrar de mim") existe mas **nenhuma tela o usa** — dead code.
- `TransactionRepository.findByAccount_IdAccount` e `findByAccountAndSource` foram substituídos por
  `searchByAccount` e não têm mais chamador — dead code.
- `TransactionEngineService.categorize(String, List)` (2 args) só é usado por testes.
- `CategorySeeder` roda como `CommandLineRunner` a cada boot em vez de ser uma migration Flyway
  (`V5__seed_categories.sql`) — inconsistente com o resto da estratégia de schema.
- `AnalyticsService` usa `LocalDate.now()` sem timezone explícito; se o servidor rodar em UTC e o usuário
  estiver em `America/Sao_Paulo`, o "mês atual" vira até 3h errado na virada. Configure
  `spring.jackson.time-zone` e passe a zona explicitamente.
- `spring.jpa.hibernate.ddl-auto=validate` + Flyway: **excelente escolha**, mantenha.

---

## 3. Regras de negócio a melhorar

### B-01 · Importação de CSV descarta o sinal do valor ⚠️ *mais impactante*

**Onde:** [Importar.tsx:36](front-end/src/pages/Importar/Importar.tsx#L36) + [TransactionEngineService.java:60](back-end/src/main/java/com/dev/nexusfinance/services/TransactionEngineService.java#L60)

```typescript
const amount = Math.abs(Number(normalizedAmount))   // Importar.tsx
```
```java
|| input.amount().signum() < 0) throw new IllegalArgumentException(...)  // service rejeita negativos
```

O front-end aplica `Math.abs()` e **não envia o campo `type`**. Então:

- Um extrato real (onde débito = negativo, crédito = positivo) **perde a informação de direção**.
- O tipo passa a ser inferido **só por palavra-chave**, com fallback para `EXPENSE`.
- Consequência prática: um **estorno de R$ 89,90 do iFood** é importado como uma **despesa** de R$ 89,90
  em Alimentação. O usuário "paga" duas vezes no relatório.
- Pior: o teste `mantemInferenciaDeTipoParaImportacaoSemTipo` **documenta esse comportamento como
  intencional**, então o bug está congelado por teste.

**Correção:** o sinal do valor é a fonte de verdade mais confiável num extrato bancário. Derive o tipo dele:

```typescript
const numeric = Number(normalizedAmount)
const type = numeric < 0 ? 'EXPENSE' : 'INCOME'
const amount = Math.abs(numeric)
```

E no motor de categorização, use o `type` como restrição (o método já suporta isso — é o mesmo caminho
do lançamento manual). Precedência sugerida: coluna `tipo` do CSV → sinal do valor → inferência por
keyword. Atualize o teste para refletir a nova regra.

### B-02 · Importação sem idempotência — reimportar duplica tudo

Não há detecção de duplicata. Importar o mesmo extrato duas vezes (cenário comum: o usuário não lembra se
já importou) **dobra todos os valores** do mês, silenciosamente. Para um sistema cuja proposta central é
ingestão de extratos, essa é a lacuna funcional mais séria.

**Correção:** gere uma *fingerprint* determinística por transação e crie uma UNIQUE nela:

```sql
ALTER TABLE tb_transactions ADD COLUMN fingerprint BINARY(32) NOT NULL;
ALTER TABLE tb_transactions ADD CONSTRAINT uk_tx_fingerprint UNIQUE (id_account, fingerprint);
```

`fingerprint = SHA-256(id_account | transaction_date | raw_description | amount)`

E devolva um resultado rico em vez de só um contador:

```json
{ "inserted": 1420, "duplicatesSkipped": 80, "rejected": 0, "importId": "..." }
```

Complemente com uma tabela `tb_import_batches` (arquivo, hash, data, usuário, contadores) que permita
**desfazer uma importação inteira** — funcionalidade que os apps de finanças de verdade têm e que os
usuários pedem imediatamente.

### B-03 · "Saldo da conta" no dashboard não é saldo

**Onde:** [Dashboard.tsx:47](front-end/src/pages/Dashboard/Dashboard.tsx#L47) usando `summary.balance` de
[AnalyticsService.getSummary](back-end/src/main/java/com/dev/nexusfinance/services/AnalyticsService.java#L22-L32)

`AnalyticsService` calcula `income - expense` **apenas do mês corrente**. O dashboard rotula esse número
como o **saldo da conta**. No dia 1º de cada mês, o saldo de todo mundo "zera" — e no dia 2, o saldo de
quem gastou R$ 50 aparece como **−R$ 50**.

**Correção:** separe dois conceitos que hoje estão fundidos:

- **Saldo da conta** = saldo inicial + soma de todas as transações desde sempre. Exige um campo
  `opening_balance` em `tb_accounts` (hoje inexistente) e uma query sem filtro de data.
- **Resultado do mês** = o que o Analytics calcula hoje. Rotule como "Entradas − Saídas do mês".

O `Relatorios.tsx` repete o mesmo número em dois cards diferentes ("Saldo Total" e "Economia do Mês"),
o que reforça que o conceito precisa ser desambiguado.

### B-04 · Analytics travado no mês corrente, sem histórico e sem comparação

`getSummary` é hardcoded em `LocalDate.now().withDayOfMonth(1)`. Não há como ver o mês passado,
comparar meses, ver evolução ou filtrar por período. A tela de Relatórios exibe 4 cards estáticos —
nenhum gráfico, apesar de o README prometer insights.

**Correção:** `GET /analytics/summary?from=&to=` + `GET /analytics/timeseries?months=12`, com agregação
por mês feita em SQL. No front, um gráfico de barras (entradas × saídas por mês) e um donut de categorias.

### B-05 · Planejamento e realidade não se conversam

O módulo de gastos previstos é o código mais bem escrito do projeto (lock pessimista, `@Version`,
validação monetária rigorosa, testes de isolamento entre usuários) — mas ele vive **completamente
isolado**. `MonthlyForecast` não tem relação com `Category` nem com `Transaction`.

Isso significa que a pergunta central de um app de finanças pessoais — **"eu gastei mais do que planejei?"**
— não pode ser respondida.

**Correção (maior ganho de produto do projeto):**

1. Adicione `id_category` (nullable) em `tb_planned_expenses`.
2. Crie `GET /forecasts/{month}/tracking` retornando, por categoria:
   `{ categoria, planejado, realizado, diferença, percentualConsumido }`.
3. Na tela, barra de progresso por categoria com alerta ao ultrapassar 100%.

Isso também é o que dá substância ao **Nex** (o assistente do README): "Você já usou 92% do previsto em
Alimentação e ainda faltam 11 dias" é um insight computável a partir desses dados — sem precisar de LLM.

### B-06 · Motor de categorização é frágil e não aprende

**Onde:** [TransactionEngineService.categorize](back-end/src/main/java/com/dev/nexusfinance/services/TransactionEngineService.java#L79-L94)

- `contains()` simples: a keyword `"AGUA"` casa com `"GUARDA AGUANIL"`; e `"PIX"` já foi removida da
  descrição pelo `cleanDescription` antes de chegar aqui.
- A ordem de precedência é a **ordem de inserção no banco** — a primeira categoria criada ganha, o que
  é arbitrário e não determinístico entre ambientes.
- `categories.findAll()` a cada lote, sem cache — com 10.000 transações é O(n × categorias × keywords)
  em memória, refazendo `split(",")` e `toUpperCase()` a cada linha.
- Não há **recategorização manual**: se o motor errar, o usuário não tem como corrigir. Não existe
  `PATCH /transactions/{id}` no projeto.
- O sistema não aprende com a correção.

**Correção, em três etapas:**

1. **Curto:** matching por palavra inteira (`\b` regex), keywords normalizadas (sem acento, uppercase)
   pré-compiladas uma vez, campo `priority` para desempate determinístico, e cache com
   `@Cacheable("categories")`.
2. **Médio:** `PATCH /transactions/{id}` para o usuário corrigir a categoria + campo
   `categorized_by ENUM('ENGINE','USER')` para não sobrescrever correção manual.
3. **Longo:** quando o usuário recategoriza, ofereça "aplicar a todas as transações com essa descrição"
   e crie uma **regra pessoal** (`tb_category_rules` com `id_user`) — o motor consulta as regras do
   usuário antes do dicionário global. É aprendizado sem ML.

### B-07 · Transações são imutáveis e não têm exclusão segura

Não existe `PUT`/`PATCH` de transação. Digitou o valor errado no lançamento manual? O único recurso é o
`DELETE` — que é justamente o endpoint vulnerável do S-01 e que faz **hard delete**.

**Correção:** adicione edição (com auditoria do valor anterior) e converta o delete em **soft delete**
(`deleted_at`), preservando a trilha. Em sistema financeiro, dado apagado de verdade é dado que ninguém
consegue auditar depois.

### B-08 · Validações de domínio ausentes

| Regra | Situação hoje |
|---|---|
| Data futura em transação | Aceita `2099-12-31` (validado só no `max` do input HTML, contornável) |
| Data muito antiga | Aceita `0001-01-01` |
| Valor máximo de transação | `DECIMAL(38,2)` sem `CHECK` — aceita R$ 10²⁵ (compare: `tb_planned_expenses` **tem** `CHECK (amount > 0)`) |
| CPF válido | Só conta 11 dígitos; `00000000000` passa |
| Transação com valor zero | Aceita (`signum() < 0` rejeita só negativo) |
| Múltiplas contas com o mesmo nome | Aceita duplicata |
| Limite de contas por usuário | Ilimitado, apesar de existir `PlanType.BASIC/PREMIUM` |

### B-09 · `PlanType` existe mas não faz nada

`BASIC`/`PREMIUM` está no modelo, na migration e é setado no cadastro — e **nenhuma regra o consulta**.
Não há diferença de comportamento, limite ou feature entre os planos. Ou remova, ou implemente (limite de
contas, de importações/mês, de histórico de analytics).

### B-10 · Não há troca nem recuperação de senha

Não existe `PUT /users/me/password` nem fluxo de "esqueci minha senha". Um usuário que suspeite de
comprometimento **não tem como trocar a própria senha** — e, com o S-06, nem invalidar as sessões ativas.
Para um app financeiro, é um requisito básico faltando.

---

## 4. Roadmap de estudo — mapeando os 6 temas no projeto

Esta é a parte mais interessante: o Nexus Finance é um **campo de estudo quase perfeito** para os seis
tópicos do backlog, e cada um tem um ponto de entrada natural no código atual. A ordem abaixo é
proposital — cada etapa cria a base para a seguinte.

### 4.1 · Envelopamento Criptográfico (DEK / KEK) ✅ *já concluído no board — hora de aplicar*

**Onde encaixa:** S-08 (CPF e descrições em claro).

O conceito já está estudado; o projeto tem o caso de uso ideal para praticar:

```
KEK (Key Encryption Key)  →  em variável de ambiente / AWS KMS / HashiCorp Vault
     │  cifra
     ▼
DEK (Data Encryption Key) →  uma por usuário, guardada cifrada em tb_user_keys
     │  cifra
     ▼
Dados sensíveis           →  cpf, raw_description, clean_description
```

**Implementação sugerida:**

1. Migration `V5`: `tb_user_keys (id_user PK, wrapped_dek VARBINARY(256), kek_version INT, created_at)`.
2. `KeyManagementService`: gera DEK (AES-256) no cadastro, envelopa com a KEK, persiste.
   Cache das DEKs desembrulhadas em memória com TTL curto (Caffeine).
3. `CryptoConverter implements AttributeConverter<String, byte[]>` — o Hibernate cifra/decifra
   transparentemente via `@Convert`. AES-256-GCM com nonce aleatório de 96 bits por registro, e o
   `id_user` como *Additional Authenticated Data* (impede trocar um blob de um usuário por outro).
4. **Índice cego** para busca: `cpf_hash BINARY(32) = HMAC-SHA256(cpf, pepper)` com UNIQUE.
   `findByCpf` passa a buscar pelo hash. Sem isso, o login e a checagem de duplicidade quebram.
5. **Rotação de KEK:** um job que re-envelopa todas as DEKs sem tocar em um único dado cifrado —
   essa é a beleza do padrão, e o exercício que fixa o conceito.

**Armadilha para estudar de propósito:** tente cifrar `amount` e veja o `SUM()` do Analytics quebrar.
Entender *por que* alguns campos não podem ser cifrados sem repensar as queries vale mais que o
código em si.

### 4.2 · Fluxo OAuth 2.0 com PKCE

**Onde encaixa:** S-06 (token artesanal) + S-17 (botão do Google que não faz nada).

O `AuthService` é um mini-JWT feito à mão. Ele funciona e é bem escrito, mas é exatamente o tipo de
coisa que OAuth 2.0 existe para você **não** ter que manter.

**Trilha sugerida:**

1. **Consumir primeiro (mais fácil):** implemente de verdade o login com Google usando
   Authorization Code + PKCE. Troque `useGoogleLogin` (fluxo implícito, desencorajado) pelo
   `authorization_code` flow: o front gera `code_verifier` + `code_challenge` (S256), o back-end troca
   o code por tokens e valida o `id_token` (assinatura, `iss`, `aud`, `exp`, `nonce`).
   **Estude a diferença:** por que o PKCE existe e o que ele resolve que o `state` sozinho não resolve.
2. **Ser o provedor depois:** substitua o `AuthService` pelo **Spring Authorization Server**. Você ganha
   JWT com `kid`/`jti`, JWKS endpoint, refresh token rotativo e revogação — resolvendo o S-06 inteiro
   como efeito colateral.
3. **Ligação com o próximo tema:** o Open Finance brasileiro usa OAuth 2.0 + PKCE **obrigatoriamente**,
   com as restrições do FAPI por cima (§4.5).

### 4.3 · Criptografia Ágil no Spring Boot

**Onde encaixa:** S-04 (PBKDF2 hardcoded) e §4.1 (versão de algoritmo nos dados cifrados).

*Crypto agility* é a capacidade de **trocar de algoritmo sem migrar os dados de uma vez.** O projeto já
tem o embrião disso — repare no formato do próprio hash:

```java
return "pbkdf2$" + ITERATIONS + "$" + salt + "$" + hash;   // PasswordService.hash
```

Esse prefixo **é** um marcador de algoritmo. O `matches` até lê o número de iterações do registro
(permitindo aumentar o custo sem invalidar hashes antigos) — mas para por aí: o algoritmo em si é fixo,
e o fallback do S-04 é o oposto de ágil.

**Exercícios concretos, em ordem:**

1. Troque a implementação manual pelo `DelegatingPasswordEncoder` do Spring Security, com Argon2id como
   padrão e PBKDF2 mantido só para leitura. Observe como o prefixo `{argon2id}` faz o mesmo papel do seu
   `pbkdf2$`, mas com um registry de algoritmos por trás.
2. Implemente **re-hash oportunista**: no login bem-sucedido, se o hash usa algoritmo/custo antigo,
   re-hasheie com o atual e salve. A base migra sozinha, sem downtime e sem pedir senha nova a ninguém.
   (`PasswordEncoder.upgradeEncoding()` existe exatamente para isso.)
3. Aplique o mesmo raciocínio aos dados cifrados de §4.1: prefixe cada blob com
   `versão_algoritmo | kek_version | nonce | ciphertext`, e faça o decrypt escolher a implementação pela
   versão lida. Assim `AES-256-GCM` → `XChaCha20-Poly1305` (ou → PQC) vira mudança de configuração.
4. Faça o mesmo com o token: adicione `alg` e `kid` ao payload.

### 4.4 · Pluggy Connect & Sandbox Integration

**Onde encaixa:** substitui o CSV manual pela ingestão real de Open Finance — o passo que transforma
o projeto de simulação em produto.

O motor de ingestão (`TransactionEngineService`) **já está pronto** para receber dados de outra fonte:
ele recebe `List<TransactionInput>` e não sabe de onde veio. Isso é uma boa notícia arquitetural.

**O que precisa acontecer antes:**

- **B-02 (idempotência) é pré-requisito absoluto.** Um sync com Pluggy roda periodicamente e traz
  transações que se sobrepõem — sem fingerprint, você duplica a base a cada sincronização.
- **B-01 (sinal do valor)** também: a API da Pluggy devolve valores com sinal e um campo de tipo
  (`DEBIT`/`CREDIT`). Se o modelo descarta o sinal, você perde a informação já na entrada.

**Implementação sugerida:**

1. Sandbox da Pluggy + Pluggy Connect (widget) no front — repare que o fluxo de consentimento **é**
   OAuth-like, o que amarra com §4.2.
2. `tb_bank_connections (id, id_user, pluggy_item_id, status, consent_expires_at, last_synced_at)`.
   O `access_token` da Pluggy é credencial sensível → cifre com a DEK do usuário (§4.1).
3. Webhook `/api/v1/webhooks/pluggy` para atualização de item — **valide a assinatura HMAC do webhook**,
   nunca confie no payload cru. Bom exercício de segurança.
4. Job de sincronização (`@Scheduled`) + backoff exponencial + tratamento de consentimento expirado
   (no Open Finance BR o consentimento tem prazo e precisa ser renovado — modele isso).
5. Mantenha o import CSV como fallback. `TransactionSource` já tem o enum; adicione `OPEN_FINANCE`.

### 4.5 · Padrões FAPI e mTLS (Open Finance)

**Onde encaixa:** é o nível de rigor que o Bacen exige de quem participa do Open Finance de verdade —
e o contraste com o estado atual do projeto é o que torna o estudo valioso.

**Diagnóstico honesto:** hoje o Nexus está longe do FAPI. O interessante é usar o checklist como espelho:

| Requisito FAPI 2.0 | Nexus hoje |
|---|---|
| mTLS ou `private_key_jwt` para autenticar o cliente | Nenhuma autenticação de cliente |
| Tokens *sender-constrained* (mTLS-bound ou DPoP) | Bearer puro (S-06) |
| PAR — Pushed Authorization Requests | N/A |
| JARM — respostas assinadas | N/A |
| PKCE obrigatório com S256 | N/A (§4.2) |
| TLS 1.2+ com cipher suites restritas | Não configurado (S-15) |
| Sem tokens em `localStorage` | Token em `localStorage` (S-06) |

**Exercícios práticos:**

1. Configure **mTLS** no Spring Boot: gere uma CA própria, emita certificados de cliente e servidor,
   configure `server.ssl.client-auth=need` e um `AuthenticationProvider` X.509. Fazer isso funcionar
   ponta a ponta ensina mais sobre PKI do que qualquer artigo.
2. Implemente **DPoP** (RFC 9449) sobre o seu token: o cliente prova posse de uma chave privada a cada
   request. Resolve o problema de "token roubado = acesso" do S-06 de forma elegante.
3. Adicione os headers de segurança do S-15 e configure o TLS com cipher suites restritas.
4. Leia o *Security Profile* do Open Finance Brasil e marque, item a item, o que falta. Esse documento
   é a melhor fonte prática que existe em português para esses conceitos.

### 4.6 · Criptografia Pós-Quântica (PQC)

**Onde encaixa:** é o horizonte — e, feito na ordem certa, custa pouco.

Se você implementar a **agilidade criptográfica** de §4.3 corretamente, PQC deixa de ser um projeto
e vira uma troca de configuração. Esse é justamente o argumento central do tema: *harvest now, decrypt
later* — dados financeiros e CPF capturados hoje podem ser decifrados daqui a 15 anos por um computador
quântico, e o CPF de alguém não muda nunca.

**Trilha:**

1. **Inventário criptográfico:** mapeie tudo que o projeto usa e classifique por risco quântico:

   | Uso | Algoritmo | Risco quântico | Ação |
   |---|---|---|---|
   | Hash de senha | PBKDF2-HMAC-SHA256 | Baixo (Grover só reduz pela metade) | Manter, aumentar custo |
   | Assinatura do token | HMAC-SHA256 | Baixo (simétrico) | Manter |
   | Dados em repouso (§4.1) | AES-256-GCM | Baixo (256 bits → 128 efetivos) | Manter |
   | **TLS — troca de chaves** | **ECDHE (X25519)** | **Alto (Shor quebra)** | **Migrar** |
   | **TLS — certificados** | **RSA / ECDSA** | **Alto (Shor quebra)** | **Migrar** |
   | **mTLS do FAPI (§4.5)** | **RSA / ECDSA** | **Alto** | **Migrar** |

   A conclusão desse exercício é reveladora: o risco quântico do projeto **não está nos dados cifrados —
   está no transporte e nas assinaturas.**

2. **Híbrido primeiro:** habilite `X25519MLKEM768` (ML-KEM/Kyber combinado com X25519) no TLS. Java 24+
   já traz ML-KEM (JEP 496) e ML-DSA (JEP 497) na JDK; em Java 17, use BouncyCastle com o provider PQC.
   Híbrido é a recomendação atual porque protege contra falhas no algoritmo novo *e* contra o quântico.
3. **ML-DSA (Dilithium)** para assinar tokens, se você migrar de HMAC para assinatura assimétrica em §4.2.
4. Documente no README a decisão e o racional — um "Cryptographic Inventory" é entregável real de
   segurança e ficaria excelente num portfólio.

---

## 5. Inovação de produto — fechando o ciclo

### 5.1 · O diagnóstico antes das ideias

O Nexus tem hoje quatro módulos bem definidos — e **quatro ilhas**:

```
   Ingestão          Categorização         Planejamento          Analytics
   (CSV)      ──▶    (dicionário)    ✗     (Forecast)      ✗     (mês atual)
                                     └───── não se falam ────────┘
```

Todo app de finanças pessoais que funciona é um **ciclo**:

```
        ┌──────────────────────────────────────────────┐
        │                                              │
   você planeja  ──▶  gasta  ──▶  o sistema mede  ──▶  aprende
        ▲                                              │
        └──────────────────────────────────────────────┘
                    ↑ as duas setas que faltam
```

Hoje o Nexus faz "gasta → mede" e para. As setas **"mede → aprende"** e **"aprende → planeja"** não
existem. A consequência prática é dura: o usuário digita gasto previsto na mão, todo mês, para sempre,
e recebe de volta um relatório estático do mês corrente. O módulo mais bem construído do projeto
(`MonthlyForecastService`) é o que tem **maior chance de ficar vazio depois da segunda semana** — porque
o custo de preenchê-lo é 100% do usuário e o retorno é um número que ele já sabia.

Isso é um problema de produto maior que qualquer feature ausente. Inovar aqui não é adicionar a décima
tela — **é fechar essas duas setas.** Tudo abaixo serve a isso.

**O diferencial que já existe e está subaproveitado:** o `cleanDescription()` transforma
`COMPRA VISA*1234 UBER EATS SAO PAULO` em `UBER EATS`. Isso significa que o sistema tem algo que a
maioria dos concorrentes não tem de graça: **um identificador estável de estabelecimento**. Descrições
brutas variam a cada compra (terminal, cidade, sufixo); a limpa, não. Quase toda a inovação abaixo se
apoia nesse único fato.

> **Como ler cada item:** *Dor* é o problema do usuário, não a limitação técnica. *Regra de negócio que
> sustenta* lista as entidades e invariantes **que já existem no código** e tornam a ideia viável.
> *Limite* é o que a feature não pode violar sem quebrar o domínio — é a parte que costuma ser esquecida.
> Esforço é relativo (P/M/G), não estimativa de prazo.

---

### 5.2 · As propostas

#### P-01 · Motor de recorrência — "as assinaturas que você esqueceu" · Esforço M

**Dor real.** Todo mundo paga de 2 a 5 assinaturas que não usa e não lembra que existem. Ninguém abre o
extrato para caçar R$ 29,90. É a dor mais universal e mais mensurável em finanças pessoais — e a que dá
retorno financeiro imediato ao usuário no primeiro dia de uso.

**O que é.** Um detector que agrupa transações por `cleanDescription` dentro da conta e identifica
padrões de cobrança periódica: mesma descrição limpa, valor estável, intervalo regular. Devolve:
assinaturas ativas, **valor anualizado**, data da próxima cobrança e — o mais valioso — **variação de
preço** ("subiu de R$ 19,90 para R$ 29,90 em março; você já pagou R$ 60 a mais desde então").

**Regra de negócio que sustenta.** `Transaction` já tem `cleanDescription` (estável por
estabelecimento), `transaction_date`, `amount` e vínculo com `Account`. O `CategoryType` distingue
`INCOME` de `EXPENSE` — então o mesmo motor detecta **salário recorrente** e alimenta o
`expectedIncome` do `MonthlyForecast` (P-02). Nada disso precisa de dado novo: é agregação sobre o que
já está persistido.

Nova entidade sugerida:
`tb_recurrences (id, id_account, clean_description, expected_amount, cadence_days, occurrences, last_seen_at, next_expected_at, status)`.

Heurística inicial: ≥3 ocorrências · intervalo 28±5 dias (ou 7/15/365 para semanal/quinzenal/anual) ·
valor dentro de ±15%.

**Limite.** Depende de **B-02 (idempotência)**: sem deduplicação de importação, três importações do
mesmo CSV viram "recorrência diária" e o motor vira gerador de lixo. E não pode confundir repetição no
**mesmo dia** com periodicidade — isso é P-06, e o tratamento é oposto.

**Depende de:** B-02, B-01 (tipo correto) · **Destrava:** P-02, P-03, P-05, P-08

---

#### P-02 · O planejamento que se preenche sozinho · Esforço M

**Dor real.** Preencher gasto previsto na mão é trabalho chato com retorno abstrato. É exatamente por
isso que módulos de orçamento são abandonados — em qualquer app, não só neste. A dor não é "não sei
planejar", é **"não vou digitar 14 linhas todo dia 1º"**.

**O que é.** Ao abrir um mês vazio, o sistema propõe o planejamento pronto: recorrências detectadas
(P-01) com data e valor, mais a **mediana por categoria dos últimos 3 meses** para os gastos variáveis.
O usuário aceita, ajusta ou descarta cada linha. O trabalho vira revisão de 30 segundos em vez de
digitação de 10 minutos.

**Regra de negócio que sustenta.** `MonthlyForecast` + `PlannedExpense` já existem, com
`getOrCreate(user, month)` criando o mês sob demanda. E o `validateExpense` já impõe as invariantes que
as sugestões precisam respeitar: descrição ≤ 120 caracteres, `amount > 0` com no máximo 2 casas decimais,
e **`dueDate` obrigatoriamente dentro do mês de referência**. Uma sugestão gerada a partir de uma
recorrência do dia 31 precisa ser *clampada* para meses de 30 dias — senão o próprio serviço a rejeita.

**Limite — e este é o mais importante da seção.** A sugestão **nunca** pode virar registro sem
confirmação. O `MonthlyForecast` é a *declaração de intenção* do usuário; se o sistema preenchê-lo
sozinho, o número deixa de significar "o que eu pretendo gastar" e passa a significar "o que o sistema
acha que vou gastar" — e aí o `projectedBalance` perde todo o sentido, junto com o P-04 inteiro.
Modele estado (`SUGGESTED` → `ACCEPTED`) e nunca some sugestão no total até ser aceita.

**Depende de:** P-01, B-05 · **Efeito:** resgata o módulo mais bem escrito do projeto do abandono

---

#### P-03 · "Vai sobrar ou vai faltar?" — saldo projetado até o fim do mês · Esforço M

**Dor real.** A pergunta que a pessoa realmente faz no dia 12 não é "quanto gastei" — é **"dá pra
chegar no fim do mês?"**. Nenhuma tela do Nexus responde isso hoje. O `projectedBalance` atual é
estático (`expectedIncome − totalPlanned`): ele não sabe que já é dia 12, nem o que já foi gasto.

**O que é.** Uma curva diária: partindo do realizado até hoje, soma os `PlannedExpense` ainda não
vencidos (por `dueDate`) e as recorrências ainda não cobradas (P-01) até o fim do mês. Resultado: uma
linha que cruza — ou não — o zero, com a data em que isso acontece.

**Regra de negócio que sustenta.** O ciclo mensal já é a unidade do sistema inteiro
(`referenceMonth = month.atDay(1)`, constraint `uk_forecast_user_month`, analytics no mês corrente).
E o **`PlannedExpense.dueDate` existe, é validado, é indexado (`idx_planned_expense_forecast_due`), é
usado para ordenar a lista — e não serve para absolutamente mais nada hoje.** Este item é o que dá
propósito ao campo.

**Limite.** Isto é *projeção de fluxo do mês*, não saldo bancário. Enquanto B-03 não separar
`opening_balance` de "resultado do mês", a tela não pode chamar isso de "saldo" — seria repetir o erro
que o dashboard já comete. Rotule como "projeção do mês" e trate como estimativa, sempre.

**Depende de:** P-01, B-03 · **Sinergia:** é a tela onde o Nex faz mais sentido

---

#### P-04 · Alerta de estouro **antes** do estouro · Esforço P (depois de B-05)

**Dor real.** Descobrir no dia 30 que estourou em Alimentação não serve para nada — o dinheiro já foi.
O alerta só tem valor enquanto ainda dá para mudar o comportamento.

**O que é.** Ritmo de queima por categoria: `realizado_até_hoje / dias_decorridos × dias_do_mês`
projetado contra o previsto. Saída: *"dia 12 · Alimentação · 78% do previsto consumido · no ritmo atual
fecha em R$ 1.180 contra R$ 900 planejados"*.

**Regra de negócio que sustenta.** Exige B-05 (categoria no `PlannedExpense`) para ter os dois lados da
conta. O `AnalyticsService.sumExpensesByCategory` **já entrega o realizado por categoria** e já filtra
`CategoryType.EXPENSE` — metade do trabalho está pronta e testada.

**Limite.** Não alerte com ruído: exija mínimo de dias decorridos (≥7) e valor previsto mínimo, senão
uma compra grande no dia 2 dispara alarme falso em todo mundo e o usuário desliga as notificações para
sempre. Alerta que grita cedo demais é alerta que ninguém lê. Vale só para `EXPENSE` — projetar
"estouro de receita" não faz sentido.

**Depende de:** B-05 · **Efeito:** é isto que transforma o **Nex** de mascote em produto

---

#### P-05 · Custo fixo vs. variável — "quanto custa existir" · Esforço P (depois de P-01)

**Dor real.** Quase ninguém sabe responder **"quanto eu preciso ganhar por mês para não ficar no
vermelho?"**. É a informação que muda decisão grande — trocar de emprego, se mudar, assumir uma
parcela — e nenhum extrato entrega isso mastigado.

**O que é.** Separar o gasto em **fixo** (recorrências de P-01 + previstos com `dueDate`) e **variável**
(o resto), e mostrar: *"seu custo fixo é R$ 3.200/mês — 68% da sua renda prevista. Sobram R$ 1.500 para
o variável."* Com histórico, vira a métrica mais acompanhável do app.

**Regra de negócio que sustenta.** `MonthlyForecast.expectedIncome` já existe, é validado
(`allowZero = true`, ≤ 2 casas, não-negativo) e é a base natural do denominador. A classificação sai
inteira de P-01 + `PlannedExpense`.

**Limite.** Uma compra grande e única (uma passagem aérea, um notebook) **não é custo fixo** — só entra
como fixo o que P-01 confirmou como recorrente ou o que o usuário marcou explicitamente. Errar isso
inverte o significado do indicador.

**Depende de:** P-01

---

#### P-06 · Cobrança duplicada e valor fora do padrão · Esforço P

**Dor real.** Cobrança em duplicidade do mesmo estabelecimento acontece, e o usuário quase nunca
percebe. É dinheiro de volta no bolso dele — o tipo de coisa que faz alguém recomendar o app.

**O que é.** Sinalizar: mesma conta + mesma `cleanDescription` + mesmo valor + mesmo dia (ou janela de
48h) → *"possível cobrança duplicada, confira com o banco"*. E, com a base de P-01, valor muito fora do
padrão histórico daquele estabelecimento → *"você costuma pagar R$ 40 aqui; esta veio R$ 400"*.

**Regra de negócio que sustenta.** Só depende de `cleanDescription`, `amount`, `transaction_date` e
`id_account` — tudo já persistido.

**⚠️ Limite — e isto muda como o B-02 deve ser implementado.** Este item está em **tensão direta** com a
deduplicação de importação. Se a *fingerprint* do B-02 for
`SHA-256(id_account | data | descrição | valor)`, então uma cobrança **genuinamente duplicada pelo
lojista** é indistinguível de uma linha reimportada — e o sistema vai **engolir silenciosamente** a
segunda, escondendo do usuário exatamente o problema que este item existe para revelar.

**Correção do B-02 à luz disto:** inclua na fingerprint um índice de ocorrência (a *n*-ésima linha
idêntica dentro do mesmo lote). Assim, reimportar o mesmo arquivo continua deduplicando corretamente,
mas duas cobranças idênticas legítimas no mesmo extrato são preservadas — e sinalizadas para o usuário
decidir. Sem esse cuidado, B-02 e P-06 se anulam.

**Depende de:** B-02 (implementado com o ajuste acima)

---

#### P-07 · O dicionário que aprende com a base · Esforço G

**Dor real (do produto, não do usuário).** O motor de categorização depende de um dicionário estático
com 6 categorias e ~25 palavras-chave, mantido à mão. Ele erra com qualquer estabelecimento fora dessa
lista — e o erro é a primeira coisa que o usuário nota, porque contamina todos os relatórios.

**O que é.** Quando muitos usuários corrigem a **mesma `cleanDescription` para a mesma categoria**
(B-06 etapa 2), o sistema propõe promover aquele mapeamento ao dicionário global — com **aprovação
manual**, nunca automática. O dicionário melhora com o uso da base, sem ninguém catalogar merchant na mão.

**Regra de negócio que sustenta.** `Category` já é global e compartilhada; `cleanDescription` já é um
identificador estável e já foi **despido de terminal, números e cidade** pelo `cleanDescription()` — é
justamente essa sanitização que torna o agregado defensável em privacidade.

**Limite — dois, e ambos inegociáveis.**
1. **Privacidade:** o que agrega é **apenas o par `descrição limpa → categoria`**. Nunca valor, nunca
   `id_user`, nunca `raw_description` (que ainda pode carregar terminal e localização). Exija um número
   mínimo de usuários distintos antes de considerar qualquer sugestão — abaixo do limiar, o par pode
   identificar uma pessoa.
2. **Integridade:** proposta ≠ aplicação. Promover automaticamente reintroduz o **S-05** (envenenamento
   do dicionário) por outra porta. O fluxo é *consenso propõe → admin aprova*.

**Efeito colateral positivo:** este item **substitui** o `POST /categories` aberto do S-05 por um
processo com curadoria — resolve um achado de segurança virando feature.

**Depende de:** B-06 (etapas 2 e 3), S-05 opção B

---

#### P-08 · Simulador "e se" · Esforço P (depois de P-01)

**Dor real.** R$ 39,90/mês não parece nada. **R$ 478,80 por ano** parece. A distância entre esses dois
números é onde a decisão acontece — e nenhum app mostra o segundo na hora certa.

**O que é.** Em cima de qualquer recorrência ou categoria: *"cancelar isto libera R$ 478,80/ano"* ou
*"cortar 20% em Alimentação = R$ 2.160/ano"*. Uma linha de texto ao lado de cada item, sem tela nova.

**Regra de negócio que sustenta.** Aritmética direta sobre `tb_recurrences` (P-01) e sobre o agregado
por categoria que o `AnalyticsService` já produz.

**Limite.** É projeção, não promessa — rotule como estimativa e nunca a misture no `projectedBalance`,
que representa compromisso planejado, não hipótese.

**Depende de:** P-01

---

### 5.3 · O que eu **não** faria (ainda)

Um roadmap também se define pelo que fica de fora. Estes são os desvios mais tentadores:

| Ideia | Por que não agora |
|---|---|
| **Recomendação de investimento** | Atividade regulada (CVM). Sugerir alocação sem habilitação é risco jurídico real, não detalhe |
| **Score de crédito próprio** | Exige bureau, é regulado e não se sustenta com dados de um único app |
| **LLM no Nex antes das regras** | Todo insight das seções acima é **computável**. LLM sobre dado que o sistema não sabe calcular é máquina de alucinar número financeiro. Regras primeiro; LLM depois, e só como camada de redação |
| **Open Finance só para agregar saldo** | Pluggy (§4.4) dá **paridade**, não vantagem — todo concorrente tem. O diferencial é o motor + o ciclo fechado. Integre para alimentar P-01 a P-05, não para virar mais um agregador |
| **Gamificação genérica** (badges, streaks) | Sem métrica que importe por trás, vira ruído. P-04 e P-05 já são o "placar" — e são reais |
| **Multi-moeda / cripto** | `amount` nem tem moeda modelada ainda. Complexidade alta, dor baixa para o público atual |
| **Compartilhamento familiar** | Exige repensar ownership inteiro (hoje `assertOwnership` é 1:1 com o usuário). Ideia boa, momento errado — faça depois que o ciclo fechar |

---

### 5.4 · Se fosse escolher três

**P-01 → P-02 → P-04**, nessa ordem, e por um motivo específico:

1. **P-01 (recorrência)** destrava sozinho P-02, P-03, P-05 e P-08. É a peça de maior alavancagem do
   documento inteiro — e entrega valor visível ao usuário já na primeira importação.
2. **P-02 (planejamento automático)** resgata o `MonthlyForecast` do abandono. Não adianta ter o código
   mais bem escrito do projeto se a tela fica vazia.
3. **P-04 (alerta antecipado)** fecha a segunda seta do ciclo e é o que dá conteúdo real ao Nex — o
   personagem que o README promete e que hoje não tem o que dizer.

Os três são **computação pura sobre dados que o modelo já persiste**. Nenhum exige fornecedor externo,
integração nova ou vendor lock-in — só os pré-requisitos B-01, B-02 e B-05, que já precisam ser feitos
de qualquer forma por serem correções de regra de negócio.

---

### 5.5 · Backlog secundário

Ideias legítimas, mas que não fecham o ciclo — façam sentido depois:

| Evolução | Por que vale | Depende de |
|---|---|---|
| **Exportação** (CSV/OFX/PDF) | Portabilidade de dados é direito LGPD, não só feature | — |
| **PWA / offline-first** | O layout já é `MobileLayout`; falta manifest + service worker | — |
| **Notificações push** | Canal de entrega para P-04 e P-03; sem eles, não tem o que notificar | P-03, P-04 |
| **Metas de economia** | Complementa o planejamento | B-05, P-05 |
| **Anexos em transação** (nota fiscal) | Reaproveita a infra de upload já validada em `UserService` | Object storage |
| **Multi-moeda** | `amount` é `DECIMAL` sem moeda; hoje BRL está implícito no código | Migration |

---

## 6. Ordem de execução recomendada

Priorizado por **risco × esforço**, não por interesse — os itens de estudo vêm depois da base estar sólida.

### Sprint 0 — Correções críticas (algumas horas)

1. **S-01** — ownership no `DELETE /transactions/{id}` ⚠️
2. **S-02** — DTO no `POST /users` (elimina o account takeover) ⚠️
3. **S-03** — remover defaults de secret do `application.properties` ⚠️
4. **S-04** — remover o fallback de senha em texto plano
5. Teste de integração de autorização cobrindo **todos** os endpoints com id (Q-06)

### Sprint 1 — Fundação

6. **S-12** Bean Validation + CPF real · **S-16** exception handlers completos
7. **S-11** logging estruturado + auditoria · **S-07** rate limiting no login
8. **S-09** timing oracle · **S-10** exclusão de usuário
9. **Q-01** Spotless · **Q-07** CI com testes + OWASP dependency-check
10. **Q-02** renomear campos `snake_case`

### Sprint 2 — Regras de negócio

11. **B-01** sinal do valor no CSV ⚠️ (corrigir o teste junto)
12. **B-02** idempotência de importação ⚠️ (pré-requisito da Pluggy)
13. **B-03** separar saldo de resultado do mês
14. **B-06** motor de categorização + recategorização manual
15. **B-05** planejado × realizado (maior ganho de produto)
16. **B-10** troca de senha · **B-07** edição e soft delete de transação
17. **Q-05** endpoints agregados (resolve o N+1 e o bug do `Relatorios.tsx`)

### Sprint 3 — Produto: fechar o ciclo

18. **P-01** motor de recorrência (maior alavancagem: destrava P-02, P-03, P-05 e P-08)
19. **P-02** planejamento pré-preenchido — *sempre como sugestão, nunca como registro*
20. **P-04** alerta de estouro antecipado — dá conteúdo real ao Nex
21. **P-06** cobrança duplicada · **P-08** simulador "e se" (ambos baratos depois de P-01)
22. **P-03** projeção diária do mês · **P-05** custo fixo vs. variável

> ⚠️ **Ao implementar o B-02 (Sprint 2), leia o P-06 antes.** A fingerprint precisa de um índice de
> ocorrência, senão a deduplicação de importação passa a esconder cobranças duplicadas legítimas —
> e os dois itens se anulam.

### Sprint 4+ — Estudo (na ordem dos pré-requisitos)

23. **§4.1** DEK/KEK sobre CPF e descrições — *o tema já dominado, agora aplicado*
24. **§4.3** Criptografia ágil — re-hash oportunista + versionamento de algoritmo
25. **§4.2** OAuth 2.0 + PKCE — consumir (Google) → depois prover (Spring Authorization Server)
26. **§4.4** Pluggy Sandbox — *só depois de B-01, B-02 e, idealmente, P-01*
27. **§4.5** FAPI + mTLS + DPoP
28. **§4.6** PQC — inventário criptográfico → TLS híbrido

---

## Observação final

Os três itens críticos são **pontuais** — não são sintoma de arquitetura ruim. São dois endpoints que não
acompanharam a evolução do resto do código e um default de configuração perigoso. O
`MonthlyForecastService` prova que o padrão de qualidade buscado já está no projeto; o trabalho é
**nivelar o resto por cima dele**, não reescrever.

O ponto que eu destacaria acima de todos: **escreva o teste de autorização do Sprint 0 antes de corrigir
o S-01**. Ele falha, você corrige, ele passa — e a partir daí nenhum endpoint novo entra sem checagem de
dono. É o único item desta lista que impede a categoria inteira de bug de voltar.
