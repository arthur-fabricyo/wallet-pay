# Operações de saldo: concorrência, idempotência, limites e auditoria

Este é o padrão central do projeto. **Qualquer operação futura que mexa em `wallets.balance`** (Pix, automações, cofres,
estornos) deve segui-lo. Specs: [10](../../docs/specs/10-balance-concurrency.md), [12](../../docs/specs/12-idempotency.md),
[13](../../docs/specs/13-audit.md), [14](../../docs/specs/14-operation-limits.md).

## Estrutura em duas camadas

```text
TransactionController
  └─ TransferService          (camada externa — SEM @Transactional)
       │  balanceRetryTemplate.invoke(...)
       │  em falha de negócio: AuditLogger.recordFailure(...) e relança
       └─ CreateTransferUseCase   (@Transactional — regra de negócio)
```

Depósito é igual: `DepositService` → `CreateDepositUseCase`.

- O retry fica **fora** da transação: uma nova tentativa precisa de um novo contexto de persistência com dados atuais.
- São dois beans distintos: chamada interna da mesma classe não passa pelo proxy do Spring.
- `RetryTemplate` programático (Spring Framework 7, `org.springframework.core.retry`), não `@Retryable`, para poder
  converter a exceção final e auditar a falha **uma vez**.

## Ordem dentro do use case transacional

1. Idempotência: remove chave expirada → `INSERT … ON CONFLICT DO NOTHING`.
   - Chave existente com mesmo hash → devolve a resposta salva (`Idempotent-Replayed: true`), sem executar nada e sem novo evento de auditoria.
   - Chave existente com hash diferente → `422 IDEMPOTENCY_KEY_REUSED`.
2. Valida valor (`Money.parse`) e carrega as carteiras.
3. **Limites** (transferência): por operação → noturno por operação → diário → mensal → janela noturna. Depósito: por operação → diário.
4. Valida saldo.
5. Movimenta (débito/crédito) e cria a transação `COMPLETED`.
6. Grava a resposta na linha de idempotência e o evento de auditoria de sucesso (`AuditLogger.record`, propagação `MANDATORY`).
7. Commit: o Hibernate grava as carteiras com `WHERE version = ?`; o Envers grava as revisões.

## Concorrência

- `wallets.version` + `@Version`. Conflito → `OptimisticLockingFailureException` → nova tentativa completa.
- `maxRetries=3` (até 4 chamadas), atraso 50 ms + jitter 25 ms. Esgotou → `409 BALANCE_CONFLICT`, sem alterar saldo.
- Só `OptimisticLockingFailureException` dispara retry; erro de negócio nunca.
- `hibernate.order_updates=true` ordena os UPDATEs por PK, evitando deadlock em transferências cruzadas.
- Como toda operação altera a versão da carteira, o cálculo de limites não precisa de lock extra: uma operação concorrente
  gera conflito e a retentativa recalcula o valor já utilizado.
- Invariantes testadas: saldo nunca negativo; soma dos saldos preservada por transferências.

## Idempotência

- Tabela `idempotency_keys` no PostgreSQL (não Redis): chave, débito, crédito e transação confirmam ou desfazem **juntos**.
- Chave única por `(user_id, operation, idempotency_key)`; formato `[A-Za-z0-9_-]{1,100}`.
- Hash SHA-256 do comando **normalizado** (`"10.0"` e `"10.00"` são iguais).
- Só respostas de sucesso são salvas; erro de negócio desfaz a transação e libera a chave.
- Validade de 24 h; job `IdempotencyKeyCleanupJob` às 03:00 (São Paulo), lotes de 1000.

## Limites

- Globais em `walletpay.limits.*` (validados na subida). Efetivo = `min(global, usuário)`; `null` = usa o global.
- Usuário só pode ficar **abaixo ou igual** ao teto global.
- Períodos calculados em `America/Sao_Paulo`; janela noturna 20:00–06:00 atravessa a meia-noite.
- Valor utilizado = soma de transações `COMPLETED` do tipo certo no período (query agregada em `TransactionRepository`).

## Auditoria

- `audit_events`: sucesso na transação da operação; **falha em transação própria** (`REQUIRES_NEW`), senão o rollback a apagaria.
- Envers: estado das entidades após cada commit. `wallets_aud` funciona como histórico de saldo.
- Metadados nunca contêm senha, hash, token ou documento completo (documento mascarado).
- Não use `UPDATE`/`DELETE` em massa em entidades auditadas: o Envers não captura.

## Checklist para uma nova operação de saldo

- [ ] Camada externa sem `@Transactional` usando `balanceRetryTemplate` + camada interna `@Transactional` em outro bean.
- [ ] Lançar `BusinessException` com `ErrorCode`; registrar falha com `AuditLogger.recordFailure`.
- [ ] Exigir `Idempotency-Key` e novo valor em `IdempotentOperation`.
- [ ] Usar `Money` e `Clock`.
- [ ] Definir se entra nos limites (e em qual) e em `AuditAction`.
- [ ] Teste de concorrência com Testcontainers.
