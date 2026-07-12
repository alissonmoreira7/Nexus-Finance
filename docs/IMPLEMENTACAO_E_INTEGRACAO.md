# Implementação e integração — Nexus Finance

## Objetivo e resultado

O backend foi completado conforme `docs/contexto.md` e integrado ao React. O fluxo agora é real: cadastro → login → conta inicial → dashboard com analytics e extrato. Os mocks do dashboard e do contexto de autenticação foram removidos.

## Arquitetura implementada

- `controllers`: autenticação, usuários, contas, categorias, transações e analytics.
- `services`: validação, hash de senha, token, autorização por propriedade da conta, categorização e cálculos.
- `repositories`: persistência paginada e agregações mensais executadas no MySQL.
- `front-end/src/services/api.ts`: cliente HTTP único, URL configurável e envio do Bearer token.
- `AuthContext`: cadastro, login, persistência da sessão e logout.
- `Dashboard`: contas, totais, categorias e transações vindos da API.

## Segurança aplicada

- Senhas são armazenadas com PBKDF2-HMAC-SHA256, salt aleatório e 210 mil iterações.
- A propriedade `password` nunca é serializada no JSON.
- Tokens têm validade de 8 horas e assinatura HMAC-SHA256.
- Rotas privadas exigem `Authorization: Bearer <token>`.
- Contas, extratos, uploads e analytics validam o proprietário autenticado.
- CORS aceita apenas a origem configurada.
- Upload limitado a 10 mil registros; paginação limitada a 100 itens.

Em produção, defina obrigatoriamente um `APP_AUTH_SECRET` longo e aleatório e restrinja `APP_CORS_ALLOWED_ORIGIN` ao domínio publicado.

## Endpoints

Rotas públicas:

- `POST /api/v1/users`: `{ "name", "cpf", "email", "password" }`.
- `POST /api/v1/auth/login`: `{ "email", "password" }`; retorna `{ token, user }`.

Rotas autenticadas:

- `GET /api/v1/users/me` e `DELETE /api/v1/users/me`.
- `POST /api/v1/accounts`: `{ "bankName" }` (o usuário vem do token).
- `GET /api/v1/accounts/user/{userId}`.
- `GET /api/v1/categories` e `POST /api/v1/categories`.
- `POST /api/v1/transactions/upload?accountId={uuid}`: array de `{ date, rawDescription, amount }`.
- `POST /api/v1/transactions/manual?accountId={uuid}`: um objeto `{ date, rawDescription, amount }`.
- `GET /api/v1/transactions/account/{accountId}?page=0&size=20&source=MANUAL|CSV` (`source` é opcional).
- `GET /api/v1/analytics/{accountId}/summary`.

As categorias Alimentação, Transporte, Moradia, Salário e Outros são criadas de forma idempotente na inicialização.

## Como executar

Pré-requisitos: Docker, Java 17+ e Node.js 18+.

```powershell
cd back-end
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Em outro terminal:

```powershell
cd front-end
npm install
npm run dev
```

Abra `http://localhost:5173`. Por padrão a API está em `http://localhost:8080/api/v1`. Para mudar, crie `front-end/.env.local`:

```dotenv
VITE_API_URL=http://localhost:8080/api/v1
```

Variáveis do backend:

| Variável | Padrão local | Uso |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/nexus_finance_db` | conexão MySQL |
| `DB_USER` | `admin` | usuário do banco |
| `DB_PASSWORD` | `admin` | senha do banco |
| `APP_AUTH_SECRET` | apenas desenvolvimento | assinatura dos tokens |
| `APP_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | origem web autorizada |

## Cenário de verificação manual

1. Cadastre nome, CPF de 11 dígitos, e-mail e senha (mínimo de 6 caracteres).
2. Confirme o redirecionamento ao dashboard e a conta “Conta principal”.
3. Obtenha o token pelo login e importe transações com o endpoint de upload.
4. Atualize o dashboard e confira totais, categorias e extrato.
5. Envie uma requisição sem token (esperado: `401`) e tente consultar conta de outro usuário (esperado: `401`).

## Decisões e limites atuais

- O resumo considera o mês corrente do relógio do servidor.
- Valores são recebidos como magnitude positiva; o tipo financeiro vem da categoria encontrada.
- O primeiro match do dicionário de palavras-chave vence; sem match é usada a categoria `Outros`.
- OAuth Google permanece visual, mas não foi conectado ao backend porque exige validação server-side e credenciais/configuração próprias.
- Importação CSV multipart ainda não foi adicionada; o lote JSON documentado é o contrato implementado.

## Frontend mobile

A área autenticada foi organizada como uma aplicação mobile-first com navegação fixa no rodapé:

- **Início:** saldo, receitas, despesas, contas, categorias e transações recentes.
- **Importar:** leitura local de CSV, validação, pré-visualização e envio do lote JSON à API.
- **Adicionar (+):** botão central destacado para registrar manualmente conta, data, descrição e valor; a categorização usa o mesmo motor do upload.
- **Histórico:** consulta paginada por conta, com identificação visual de receitas e despesas.
- O histórico permite alternar entre todas as transações, lançamentos manuais e importações CSV. Registros criados antes da inclusão da origem são tratados como CSV para preservar compatibilidade.
- **Perfil:** dados do usuário, CPF mascarado, quantidade de contas e encerramento da sessão.

O importador aceita separador `;` ou `,`, datas `AAAA-MM-DD` ou `DD/MM/AAAA` e as colunas `data`, `descricao` e `valor`. O arquivo é limitado a 2 MB e 10 mil registros. O menu considera a safe area de aparelhos com barra gestual e mantém alvos de toque maiores que 44 px.

## Validação recomendada

```powershell
cd back-end
.\mvnw.cmd test

cd ..\front-end
npm run build
npm run lint
```
