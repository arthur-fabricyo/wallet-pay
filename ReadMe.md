# OutPay — Wallet Pay

API backend de uma carteira digital, desenvolvida como projeto de portfólio com Java e Spring Boot.
A visão completa do produto (incluindo o motor de automações financeiras) está em [`docs/core.md`](docs/core.md);
as especificações de cada funcionalidade estão em [`docs/specs`](docs/specs).

**Status:** Fases 1 (núcleo da carteira) e 2 (segurança e confiabilidade) do roadmap implementadas.

## O que a aplicação já entrega

### Usuários e autenticação
- Cadastro de usuário com CPF (validação dos dígitos verificadores) ou CNPJ (consulta ao CNPJ.ws); cada usuário recebe uma carteira em BRL.
- Verificação de e-mail obrigatória antes do login, com reenvio do token.
- Login com JWT (access token de 20 minutos) e refresh token (7 dias), renovação e logout.
- Recuperação de senha por token.
- Os e-mails ainda são simulados: os tokens são escritos no log da aplicação.

### Carteira
- Consulta de saldo.
- Depósitos.
- Transferências internas entre carteiras, com comprovante.
- Extrato paginado com filtros por período e tipo de transação e ordenação.

### Segurança e confiabilidade
- **Concorrência de saldo:** lock otimista (`@Version`) com retentativa automática; se o conflito persistir, a API responde `409 BALANCE_CONFLICT`. O saldo nunca fica negativo e transferências cruzadas (A→B e B→A ao mesmo tempo) não entram em deadlock.
- **Idempotência:** depósitos e transferências exigem o header `Idempotency-Key`; repetir a requisição devolve o mesmo resultado sem duplicar a operação.
- **Auditoria:** eventos de negócio (sucessos e rejeições, incluindo login e logout) gravados na tabela append-only `audit_events`, com usuário e `requestId`; histórico das entidades (usuários, carteiras, transações) com Hibernate Envers.
- **Limites de operação:** limites globais por operação, diários e mensais, regras para o período noturno (20h às 6h, horário de Brasília) e limites personalizados que o usuário pode apenas reduzir.
- **Tratamento de falhas:** catálogo único de códigos de erro, respostas de erro padronizadas e `X-Request-Id` em toda resposta e nos logs.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/users` | Cadastrar usuário |
| `POST` | `/auth/login` | Login |
| `POST` | `/auth/refresh` | Renovar access token |
| `POST` | `/auth/logout` | Invalidar refresh token |
| `POST` | `/auth/email-verification/resend` | Reenviar token de verificação de e-mail |
| `POST` | `/auth/email-verification/confirm` | Confirmar e-mail |
| `POST` | `/auth/password-recovery/request` | Solicitar redefinição de senha |
| `POST` | `/auth/password-recovery/reset` | Redefinir senha |
| `GET` | `/wallets/me/balance` | Saldo da carteira |
| `GET` | `/wallets/me/statement` | Extrato (`page`, `size`, `from`, `to`, `type`, `sort`) |
| `POST` | `/wallets/me/deposits` | Depósito (exige `Idempotency-Key`) |
| `GET` | `/wallets/me/limits` | Consultar limites |
| `PUT` | `/wallets/me/limits` | Reduzir limites personalizados |
| `POST` | `/transactions/transfers` | Transferência (exige `Idempotency-Key`) |
| `GET` | `/transactions/{transactionId}/receipt` | Comprovante |

Com a aplicação rodando, a documentação interativa fica em `http://localhost:8080/swagger-ui.html`
(OpenAPI em `/v3/api-docs`).

## Stack

- Java 21, Spring Boot 4 (Web MVC, Security com OAuth2 Resource Server/JWT, Data JPA, Validation, Actuator)
- PostgreSQL 16, Flyway, Hibernate Envers
- springdoc-openapi
- Testes: JUnit 5, AssertJ, Testcontainers

## Como rodar

Pré-requisitos: JDK 21+ e Docker.

```bash
# Banco de dados
docker compose up -d

# Aplicação
./mvnw spring-boot:run
```

Variáveis de ambiente opcionais (os valores padrão servem para desenvolvimento local):

| Variável | Padrão |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/wallet_pay` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `wallet_pay` |
| `JWT_SECRET` | segredo de desenvolvimento (troque fora do ambiente local) |
| `CNPJ_WS_BASE_URL` | `https://publica.cnpj.ws` |

Os limites de operação são configurados em `application.properties` (`walletpay.limits.*`).

## Testes

```bash
./mvnw test
```

Os testes de integração sobem um PostgreSQL via Testcontainers, então o Docker precisa estar rodando.

## Próximos passos

Pelo [roadmap](docs/core.md#8-roadmap-sugerido): eventos e notificações (Fase 3), motor de automações (Fase 4),
organização financeira (Fase 5), pagamentos simulados via Pix (Fase 6) e aplicativo Android (Fase 7).
