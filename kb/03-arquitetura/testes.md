# Estratégia de testes

Última execução completa: **296 testes, todos verdes** (2026-11-05, após a Fase 3; testes parametrizados contam cada caso).
Contagem estática: 48 classes com testes em `src/test`. Rode `./mvnw test` para o estado real
(ver [guia](../07-guias/como-rodar-e-testar.md)). O CI roda `./mvnw -B verify` em PRs e em push para `main`.

## Camadas

| Camada | Exemplos | Observação |
|---|---|---|
| Unitários de domínio/serviço | `MoneyTest`, `CpfValidatorTest`, `LimitPeriodsTest`, `AccessTokenServiceTest`, `RequestHashTest`, `IdempotencyKeysTest`, `TransferServiceTest`, `DepositServiceTest` | Sem Mockito: fakes por subclasse ou `Proxy` |
| Use cases | `CreateTransferUseCaseTest`, `CreateDepositUseCaseTest`, `GetReceiptUseCaseTest`, `GetWalletStatementUseCaseTest`, `RegisterUserUseCaseTest` | |
| Controllers / erros | `AuthControllerTest`, `TransactionControllerTest`, `WalletControllerTest`, `GlobalExceptionHandlerTest`, `ApiAccessDeniedHandlerTest`, `ErrorCodeTest` | |
| Integração (Postgres real) | `DepositIntegrationTest`, `IdempotencyIntegrationTest`, `AuditIntegrationTest`, `EnversIntegrationTest`, `LimitsIntegrationTest`, `LimitsApiIntegrationTest`, `WalletBalanceIntegrationTest` | Testcontainers `postgres:16-alpine`, migrations Flyway reais |
| Eventos e notificações (Fase 3) | `OutboxIntegrationTest`, `TransferOutboxIntegrationTest`, `RabbitEventAdapterIntegrationTest`, `TransferNotificationIntegrationTest`, `NotificationPreferenceIntegrationTest`, `EmailChannelIntegrationTest`, `SmtpEmailSenderIntegrationTest` | Broker real; testes chamam `OutboxPublisherJob.publishPending()` à mão e esperam com Awaitility; reentrega simulada zerando `published_at`; DLQ verificada |
| Concorrência | `TransferConcurrencyIntegrationTest` | 20 threads × `10.00` numa carteira com `100.00` → exatamente 10 sucessos, saldo 0, soma preservada |
| Ponta a ponta | `FullFlowIntegrationTest` | Cadastro → e-mail → login → depósito → transferência → saldo/extrato/comprovante → auditoria → Envers |

## Infraestrutura (`src/test/.../support`)

- `AbstractIntegrationTest` — trunca todas as tabelas antes de cada teste; fixa `walletpay.events.outbox.poll-delay=PT1H`
  (o job não roda sozinho) e `walletpay.events.namespace=${random.uuid}` (filas isoladas por contexto); helpers para usuário
  verificado, saldo inicial, JWT, horário de São Paulo e execução concorrente.
- `IntegrationTestConfiguration` — containers PostgreSQL, RabbitMQ e Mailpit **compartilhados na JVM** (singletons com
  `@ServiceConnection`), `MailpitClient` e fakes `@Primary`.
- `TestUser`, `TestJwt`, `TestCpf`.
- `MutableClock` — relógio controlável para limites e expiração (sempre UTC).
- `CapturingEmailVerificationSender` / `CapturingPasswordRecoverySender` / `CapturingPushGateway` — capturam em memória.
- `RecordingAuditLogger`, `RecordingOutboxRecorder`, `PassThroughIdempotencyService`, `PermissiveOperationLimitService` —
  isolam preocupações em testes unitários.
- `InfrastructureSmokeTest`, `OptionalInfrastructureHealthTest` (broker e e-mail fora do `/actuator/health`) e
  `ArchitectureBoundaryTest` (AMQP só em `common/event/rabbit`).

## Convenções

- Docker precisa estar rodando para `*IntegrationTest`.
- Teste de concorrência simula o cliente real: repete a chamada ao receber `409 BALANCE_CONFLICT`, senão "exatamente 10 sucessos" não é determinístico.
- Testes de período usam `Clock` fixo; foco em fuso (servidor em UTC, regra em `America/Sao_Paulo`).
- Antes das mudanças da Fase 2 a base tinha 83 testes.

## Lacunas

- Sem relatório de cobertura (JaCoCo); CI não roda em push para `dev`.
- Sem testes de carga/desempenho.
- Dois testes usam `Thread.sleep` para provar ausência de efeito (pendência #32).
- Exceção à regra "sem Mockito": `TransferNotificationIntegrationTest` usa `@MockitoSpyBean` (pendência #37).
- Fase 4: testes de reentrega/duplicidade por (regra, evento), concorrência das ações de saldo e agendamento com `MutableClock`.
