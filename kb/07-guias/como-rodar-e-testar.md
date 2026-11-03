# Como rodar e testar

## Pré-requisitos

- JDK 21+ (alvo Java 21).
- Docker (banco local e testes de integração).
- Windows + Git Bash/PowerShell no ambiente atual de desenvolvimento.

## Banco de dados

```bash
docker compose up -d
```

Sobe `postgres:16-alpine` (`wallet-pay-postgres`) na porta 5432, banco/usuário/senha `wallet_pay`, volume `wallet_pay_postgres_data`.
Para recriar do zero (ex.: migration alterada): `docker compose down -v`.

## Aplicação

```bash
./mvnw spring-boot:run
```

- Swagger: `http://localhost:8080/swagger-ui.html` · OpenAPI: `/v3/api-docs` · saúde: `/actuator/health`.
- As migrations Flyway rodam na subida; `ddl-auto=validate`.

### Variáveis de ambiente (opcionais)

| Variável | Padrão |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/wallet_pay` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `wallet_pay` |
| `JWT_SECRET` | segredo de desenvolvimento — **troque fora do ambiente local** |
| `CNPJ_WS_BASE_URL` | `https://publica.cnpj.ws` |

Limites de operação: `walletpay.limits.*` em `application.properties` (ver [operações de saldo](../03-arquitetura/operacoes-de-saldo.md)).

## Testes

```bash
./mvnw test
```

Docker precisa estar rodando (Testcontainers sobe um PostgreSQL novo; não usa o banco do Compose).

### Nota sobre `JAVA_HOME`

Na máquina de desenvolvimento o `JAVA_HOME` não está definido. O plano da Fase 2 usava o JBR do IntelliJ:

```bash
export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.3/jbr"
./mvnw test
```

(O JBR 25 compila com alvo Java 21.) Rode a partir da raiz do projeto.

## Fluxo manual rápido (dev)

1. `POST /users` com CPF válido → o token de verificação aparece **no log da aplicação** (envio simulado).
2. `POST /auth/email-verification/confirm` com o token.
3. `POST /auth/login` → `accessToken` e `refreshToken`.
4. `POST /wallets/me/deposits` (header `Authorization` + `Idempotency-Key`) para ter saldo.
5. `POST /transactions/transfers` para outro usuário; `GET /transactions/{id}/receipt`; `GET /wallets/me/statement`.
6. Conferir a auditoria por SQL: `SELECT action, outcome, failure_code, request_id FROM audit_events ORDER BY occurred_at;`

## Fluxo de trabalho do projeto

1. Escrever/atualizar a **spec** em `docs/specs`.
2. Para trabalho de várias etapas, escrever um **plano** em `docs/superpowers/plans`.
3. Implementar task a task, um commit por task (conventional commits, em inglês).
4. Atualizar esta KB ao concluir ([roadmap](../01-produto/roadmap.md), [estado atual](../02-andamento/estado-atual.md)).
