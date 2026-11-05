# Arquitetura — visão geral

Monólito modular em Spring Boot. Pacote base: `ElFabrica.Wallet_pay` (`src/main/java`).

## Módulos (pacotes)

```text
ElFabrica.Wallet_pay
├── user/          cadastro, validação de CPF/CNPJ, gateway do CNPJ.ws
├── auth/          login, JWT, refresh, verificação de e-mail, recuperação de senha
├── wallet/        saldo, depósito, extrato, limites de operação
├── transaction/   transferência, comprovante, entidade Transaction, evento TransferCompleted (event/)
├── notification/  notificações: canais (push, e-mail), preferências, entrega idempotente, PushGateway (stub)
├── audit/         eventos de auditoria (audit_events) + revisão do Envers
├── common/
│   ├── error/        ErrorCode, BusinessException, GlobalExceptionHandler, 401/403 em JSON
│   ├── event/        DomainEvent, EventPublisher/EventHandler (portas), OutboxRecorder, OutboxPublisherJob
│   │   └── rabbit/   adaptador RabbitMQ — único lugar onde AMQP é permitido (ArchitectureBoundaryTest)
│   ├── email/        EmailSender (porta) + SmtpEmailSender
│   ├── idempotency/  Idempotency-Key, hash do payload, job de limpeza
│   ├── money/        Money (parse/format), InvalidAmountException
│   └── web/          RequestIdFilter (X-Request-Id + MDC)
└── config/        Security, Clock, Resilience (retry), HttpClient (timeouts), OpenAPI, Scheduling
```

Módulo previsto para a Fase 4: `automation/` (`docs/core.md` §6).

Cada módulo de negócio segue o mesmo desenho interno:

| Subpasta | Conteúdo |
|---|---|
| `controllers/` | **Somente** controllers (spec 09) |
| `dto/` | Contratos HTTP com sufixo `DTO` |
| `service/` | Use cases, serviços, comandos (`*Command`), resultados (`*Result`) e exceções de negócio |
| `domain/` | Entidades JPA e enums |
| `repository/` | Spring Data |
| `infra/` | Adaptadores externos (CNPJ.ws; envio de e-mail simulado em log) |

Tabelas de infraestrutura com SQL específico do PostgreSQL (`ON CONFLICT`, `jsonb`, `skip locked`) usam `JdbcClient` em vez
de JPA: `audit_events`, `idempotency_keys`, `outbox_events`, `notification_preferences`, `notification_deliveries`.

## Fluxo de um evento (Fase 3)

```text
UseCase (@Transactional)
  └─ OutboxRecorder.record     grava em outbox_events na mesma transação (MANDATORY)
OutboxPublisherJob (@Scheduled, 2 s)
  └─ for update skip locked    lê até 100 pendentes em ordem, publica, espera confirmação do broker, marca published_at
RabbitMQ: tópico walletpay.events, routing key = tipo do evento
  └─ fila walletpay.<consumidor>.<tipo> (+ .dlq)   uma por EventHandler; 3 retentativas com backoff, depois DLQ
      └─ EventHandler.handle   idempotente (ex.: notification_deliveries por evento+usuário+canal)
```

Entrega é *at-least-once* e sem garantia de ordem. Handlers rodam sem `SecurityContext`; o `requestId` do evento vai para o MDC.

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
   Exceção a corrigir: entidades e serviços de auth ainda usam `Instant.now()` (pendência #33).
4. **Dinheiro sempre via `Money`**; string com 2 casas na API; `NUMERIC(19,2)` no banco.
5. **Auditoria em duas camadas:** eventos de intenção/resultado (`audit_events`) + histórico de estado (Envers).
6. **`ddl-auto=validate`**: o esquema só muda por migration Flyway.
7. Testes sem Mockito: fakes por subclasse ou `java.lang.reflect.Proxy`. Exceção: `TransferNotificationIntegrationTest`
   usa `@MockitoSpyBean` (pendência #37).
8. **Operação de negócio nunca depende do broker:** só grava no outbox; publicação e consumo ficam atrás das portas de
   `common/event`.

## Stack e dependências

Spring Boot 4.1.0 (Web MVC, Security + OAuth2 resource server, Data JPA, Validation, Actuator, Flyway, AMQP, Mail),
Hibernate 7.4.1 + `hibernate-envers`, PostgreSQL 16, RabbitMQ 3, Jackson 3 (`tools.jackson`), springdoc-openapi 3.1.0, Lombok.
Testes: JUnit 5, AssertJ, Awaitility, Testcontainers 2.0.5 (`postgres:16-alpine`, `rabbitmq:3-management-alpine`, Mailpit).
CI: GitHub Actions.

**Ainda não presentes** (previstos no roadmap): Redis, Micrometer/Prometheus/Grafana, CD, provedor de e-mail transacional,
push real, ShedLock ou lock distribuído para jobs.

## Segurança HTTP

- Rotas públicas: `/users`, `/auth/login`, `/auth/refresh`, `/auth/email-verification/**`, `/auth/password-recovery/**`,
  `/actuator/health`, Swagger UI e `/v3/api-docs/**`. O restante exige JWT.
- O IP gravado na auditoria vem de `getRemoteAddr()`; `X-Forwarded-For` **não** é confiado nesta fase.
