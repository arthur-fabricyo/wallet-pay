# Arquitetura — visão geral

Monólito modular em Spring Boot. Pacote base: `ElFabrica.Wallet_pay` (`src/main/java`).

## Módulos (pacotes)

```text
ElFabrica.Wallet_pay
├── user/          cadastro, validação de CPF/CNPJ, gateway do CNPJ.ws
├── auth/          login, JWT, refresh, verificação de e-mail, recuperação de senha
├── wallet/        saldo, depósito, extrato, limites de operação
├── transaction/   transferência, comprovante, entidade Transaction
├── audit/         eventos de auditoria (audit_events) + revisão do Envers
├── common/
│   ├── error/        ErrorCode, BusinessException, GlobalExceptionHandler, 401/403 em JSON
│   ├── idempotency/  Idempotency-Key, hash do payload, job de limpeza
│   ├── money/        Money (parse/format), InvalidAmountException
│   └── web/          RequestIdFilter (X-Request-Id + MDC)
└── config/        Security, Clock, Resilience (retry), HttpClient (timeouts), OpenAPI, Scheduling
```

Cada módulo de negócio segue o mesmo desenho interno:

| Subpasta | Conteúdo |
|---|---|
| `controllers/` | **Somente** controllers (spec 09) |
| `dto/` | Contratos HTTP com sufixo `DTO` |
| `service/` | Use cases, serviços, comandos (`*Command`), resultados (`*Result`) e exceções de negócio |
| `domain/` | Entidades JPA e enums |
| `repository/` | Spring Data |
| `infra/` | Adaptadores externos (CNPJ.ws; envio de e-mail simulado em log) |

## Fluxo de uma requisição

```text
Cliente
  └─ RequestIdFilter           gera/valida X-Request-Id e põe no MDC
      └─ Spring Security       JWT (resource server); 401/403 em ApiErrorResponse
          └─ Controller        valida DTO e header Idempotency-Key
              └─ Service       camada externa: RetryTemplate, sem transação, audita falha
                  └─ UseCase   @Transactional: idempotência → limites → saldo → movimentação → audita sucesso
                      └─ Repository / JdbcClient → PostgreSQL
```

Detalhes do núcleo financeiro em [operações de saldo](operacoes-de-saldo.md).

## Princípios arquiteturais em vigor

1. **Toda operação que altera saldo** tem duas camadas (externa com retry, interna transacional) — regra obrigatória.
2. **Erro de negócio = `BusinessException` + `ErrorCode`**; o mesmo código vai para a resposta HTTP e para `audit_events.failure_code`.
3. **Um único relógio** (`Clock` em `config/ClockConfig`) para `created_at`, limites e testes.
4. **Dinheiro sempre via `Money`**; string com 2 casas na API; `NUMERIC(19,2)` no banco.
5. **Auditoria em duas camadas:** eventos de intenção/resultado (`audit_events`) + histórico de estado (Envers).
6. **`ddl-auto=validate`**: o esquema só muda por migration Flyway.
7. Testes sem Mockito: fakes por subclasse ou `java.lang.reflect.Proxy`.

## Stack e dependências

Spring Boot 4.1.0 (Web MVC, Security + OAuth2 resource server, Data JPA, Validation, Actuator, Flyway),
Hibernate 7.4.1 + `hibernate-envers`, PostgreSQL 16, springdoc-openapi 3.1.0, Lombok.
Testes: JUnit 5, AssertJ, Testcontainers 2.0.5 (`postgres:16-alpine`).

**Ainda não presentes** (previstos no roadmap): RabbitMQ, Redis, Micrometer/Prometheus/Grafana, CI/CD, provedor de e-mail.

## Segurança HTTP

- Rotas públicas: `/users`, `/auth/login`, `/auth/refresh`, `/auth/email-verification/**`, `/auth/password-recovery/**`,
  `/actuator/health`, Swagger UI e `/v3/api-docs/**`. O restante exige JWT.
- O IP gravado na auditoria vem de `getRemoteAddr()`; `X-Forwarded-For` **não** é confiado nesta fase.
