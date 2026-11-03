# Estratégia de testes

Contagem estática: **217 métodos `@Test` em 37 classes** (`src/test`). Não foi feita execução da suíte ao escrever esta página;
rode `./mvnw test` para o estado real (ver [guia](../07-guias/como-rodar-e-testar.md)).

## Camadas

| Camada | Exemplos | Observação |
|---|---|---|
| Unitários de domínio/serviço | `MoneyTest`, `CpfValidatorTest`, `LimitPeriodsTest`, `AccessTokenServiceTest`, `RequestHashTest`, `IdempotencyKeysTest`, `TransferServiceTest`, `DepositServiceTest` | Sem Mockito: fakes por subclasse ou `Proxy` |
| Use cases | `CreateTransferUseCaseTest`, `CreateDepositUseCaseTest`, `GetReceiptUseCaseTest`, `GetWalletStatementUseCaseTest`, `RegisterUserUseCaseTest` | |
| Controllers / erros | `AuthControllerTest`, `TransactionControllerTest`, `WalletControllerTest`, `GlobalExceptionHandlerTest`, `ApiAccessDeniedHandlerTest`, `ErrorCodeTest` | |
| Integração (Postgres real) | `DepositIntegrationTest`, `IdempotencyIntegrationTest`, `AuditIntegrationTest`, `EnversIntegrationTest`, `LimitsIntegrationTest`, `LimitsApiIntegrationTest`, `WalletBalanceIntegrationTest` | Testcontainers `postgres:16-alpine`, migrations Flyway reais |
| Concorrência | `TransferConcurrencyIntegrationTest` | 20 threads × `10.00` numa carteira com `100.00` → exatamente 10 sucessos, saldo 0, soma preservada |
| Ponta a ponta | `FullFlowIntegrationTest` | Cadastro → e-mail → login → depósito → transferência → saldo/extrato/comprovante → auditoria → Envers |

## Infraestrutura (`src/test/.../support`)

- `AbstractIntegrationTest` — container compartilhado com `@ServiceConnection`; helpers para usuário verificado, saldo inicial e JWT.
- `IntegrationTestConfiguration`, `TestUser`, `TestJwt`, `TestCpf`.
- `MutableClock` — relógio controlável para limites e expiração.
- `CapturingEmailVerificationSender` / `CapturingPasswordRecoverySender` — capturam tokens em memória.
- `RecordingAuditLogger`, `PassThroughIdempotencyService`, `PermissiveOperationLimitService` — isolam preocupações em testes unitários.

## Convenções

- Docker precisa estar rodando para `*IntegrationTest`.
- Teste de concorrência simula o cliente real: repete a chamada ao receber `409 BALANCE_CONFLICT`, senão "exatamente 10 sucessos" não é determinístico.
- Testes de período usam `Clock` fixo; foco em fuso (servidor em UTC, regra em `America/Sao_Paulo`).
- Antes das mudanças da Fase 2 a base tinha 83 testes.

## Lacunas

- Sem relatório de cobertura (JaCoCo) nem pipeline de CI executando a suíte.
- Sem testes de carga/desempenho.
- Quando entrarem RabbitMQ e automações: testes de integração com broker (Testcontainers) e de reentrega/duplicidade.
