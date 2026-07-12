## 1. Visão Geral do Sistema

O **Open Finance Data Engine** é um sistema de back-end focado na ingestão, sanitização e categorização automática de transações financeiras. O sistema simula o recebimento de extratos bancários brutos (Open Finance), limpa os ruídos dos dados, aplica regras de negócio para categorizar despesas e expõe esses dados via endpoints REST para consumo de interfaces web.

## 2. Requisitos do Sistema

### 2.1 Requisitos Funcionais (RF)

*O que o sistema TEM que fazer.*

- **RF01 - Ingestão de Lotes:** O sistema deve receber arquivos (CSV) contendo milhares de transações simuladas (Data, Descrição Suja, Valor, Tipo).
- **RF02 - Sanitização de Dados:** O sistema deve remover caracteres especiais, números de terminais e asteriscos das descrições (ex: transformar `COMPRA VISA*1234 UBER EATS SAO PAULO` em `UBER EATS`).
- **RF03 - Motor de Categorização:** O sistema deve classificar automaticamente a transação comparando a descrição limpa com um dicionário de palavras-chave predefinidas (ex: "UBER" -> `Transporte`; "IFOOD" -> `Alimentação`).
- **RF04 - Armazenamento Seguro:** O sistema deve persistir as transações, já limpas e categorizadas, em um banco de dados relacional garantindo a integridade referencial com o usuário e a conta bancária.
- **RF05 - API de Extrato:** O sistema deve fornecer um endpoint que retorne a lista paginada de transações de um usuário específico.
- **RF06 - API de Analytics (Resumo):** O sistema deve fornecer um endpoint que calcule o total gasto por categoria no mês atual, retornando um consolidado financeiro.

### 2.2 Requisitos Não Funcionais (RNF)

*Como o sistema tem que se comportar.*

- **RNF01 - Performance:** O endpoint de Analytics deve resolver a query matemática no banco de dados e retornar o JSON em menos de 500ms.
- **RNF02 - Padronização REST:** Todos os endpoints devem respeitar a semântica HTTP (200 OK, 201 Created, 400 Bad Request, 500 Internal Server Error).
- **RNF03 - Arquitetura Limpa:** O projeto deve separar claramente as camadas: `Controllers` (rotas), `Services` (regras de negócio) e `Repositories` (banco de dados).

## 3. Modelagem de Dados (Banco de Dados MySQL)

Você criará 4 tabelas relacionais. O Spring Data JPA (Hibernate) vai gerar isso para você se você mapear as classes corretamente.

**1. `tb_users`** (Usuários do sistema)

- `id` (PK, UUID)
- `name` (Varchar)
- `cpf` (Varchar, Unique)

**2. `tb_accounts`** (Contas bancárias do usuário)

- `id` (PK, UUID)
- `user_id` (FK -> tb_users)
- `bank_name` (Varchar) // Ex: Nubank, Itaú

**3. `tb_categories`** (O dicionário de categorias)

- `id` (PK, Long)
- `name` (Varchar) // Ex: Alimentação, Transporte, Saúde
- `type` (Enum: INCOME, EXPENSE)
- `keywords` (Varchar) // Ex: "IFOOD,MCDONALDS,BURGERKING"

**4. `tb_transactions`** (A tabela transacional pesada)

- `id` (PK, UUID)
- `account_id` (FK -> tb_accounts)
- `category_id` (FK -> tb_categories)
- `raw_description` (Varchar) // Como veio do banco
- `clean_description` (Varchar) // Como o seu motor deixou
- `amount` (Decimal)
- `transaction_date` (Date)

## 4. Documentação da API RESTful

### Endpoint 1: Ingestão de Transações

- **Rota:** `POST /api/v1/transactions/upload`
- **Ação:** Recebe o lote de dados, processa a sanitização e salva no banco.
- **Payload Esperado (JSON ou Multipart File):** Array de transações brutas.
- **Resposta (201 Created):** `{"message": "Lote processado com sucesso. 1500 registros inseridos."}`

### Endpoint 2: Extrato Paginado

- **Rota:** `GET /api/v1/transactions/account/{accountId}?page=0&size=20`
- **Ação:** Retorna o extrato limpo do usuário.
- **Resposta (200 OK):** JSON com a lista de transações limpas e seus respectivos nomes de categorias.

### Endpoint 3: Resumo Analítico (Analytics)

- **Rota:** `GET /api/v1/analytics/{accountId}/summary`
- **Ação:** Executa a totalização de gastos.
- **Resposta (200 OK):**JSON

    ```
    {
      "total_income": 5000.00,
      "total_expense": 2100.00,
      "expenses_by_category": {
        "Alimentação": 800.00,
        "Transporte": 300.00,
        "Moradia": 1000.00
      }
    }
    ```


## 5. Cronograma de Execução (Step-by-Step)

Aqui está o passo a passo para você seguir sem se perder na codificação:

### 🟢 FASE 1: O Chão de Fábrica (A sua meta para hoje à noite)

1. Acesse o **Spring Initializr** (start.spring.io).
2. Gere um projeto Maven, Java 21+ (ou o que tiver instalado). Adicione as dependências: `Spring Web`, `Spring Data JPA` e `MySQL Driver`.
3. Crie o banco de dados vazio no seu MySQL em um container Docker (`CREATE DATABASE open_finance_db;`).
4. Configure o arquivo `application.properties` no Spring para conectar no banco.
5. Crie os pacotes principais: `models`, `repositories`, `services`, `controllers`.

### 🟡 FASE 2: Modelagem (Sábado)

1. Crie as classes dentro do pacote `models` (`User`, `Account`, `Category`, `Transaction`) usando as anotações `@Entity`, `@Id`, etc.
2. Rode a aplicação e veja o Hibernate criar as tabelas magicamente no seu MySQL.
3. Insira manualmente (via DBeaver ou script SQL) 3 categorias no banco (Ex: Transporte, Alimentação, Salário) para ter dados de teste.

### 🟠 FASE 3: O Motor de Sanitização e Categorização (Sábado/Domingo)

1. No pacote `services`, crie a classe `TransactionEngineService`.
2. Crie um método `cleanDescription(String raw)`. Use Regex no Java para tirar números e caracteres estranhos.
3. Crie a lógica do motor `categorize(String cleanDesc)`. Faça o código ler a descrição limpa e procurar por palavras-chave (Ex: `if(cleanDesc.contains("UBER")) return categoriaTransporte;`).

### 🔴 FASE 4: Exposição das APIs (Domingo)

1. No pacote `controllers`, crie o `TransactionController` e o `AnalyticsController`.
2. Programe os métodos mapeados com `@PostMapping` e `@GetMapping`.
3. Injeção de Dependência: Chame os seus Services por dentro dos Controllers.
4. Teste todas as rotas usando o **Postman** ou o **Insomnia**.

### 🟣 FASE 5: A Casca Front-End (Segunda-feira antes da Bybit)

1. Crie uma pasta estática no projeto (ou um projeto HTML separado).
2. Escreva o HTML de um Dashboard simples.
3. Use o `fetch()` nativo do JavaScript para bater no seu endpoint `GET /api/v1/analytics/{accountId}/summary`.
4. Jogue o resultado num Gráfico simples (pode usar a lib Chart.js via CDN) ou numa tabela.

FASE 6 - Recomendações de IA
