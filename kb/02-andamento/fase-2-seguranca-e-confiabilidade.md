# Fase 2 — Segurança e confiabilidade ✅

Specs 10 a 15; plano em [`docs/superpowers/plans/2026-10-15-fases-1-2.md`](../../docs/superpowers/plans/2026-10-15-fases-1-2.md).
Implementada em 2026-10-20, com correções pós-revisão em 2026-10-28. Branch `feat/fases-1-2`, mergeada em `dev` em
2026-11-03 (PRs #1 e #2).

## Resultado por spec

| Spec | Tema | Entregue | Migration |
|---|---|---|---|
| [10](../../docs/specs/10-balance-concurrency.md) | Concorrência de saldo | `@Version` em `wallets`; `RetryTemplate` (3 retentativas, 50 ms + jitter 25 ms); `409 BALANCE_CONFLICT` ao esgotar; estrutura em 2 camadas; base de testes com Testcontainers | V6 |
| [11](../../docs/specs/11-deposit.md) | Depósito simulado | `POST /wallets/me/deposits`; transação `DEPOSIT` sem `sender_wallet_id`; extrato mostra `IN` com contraparte nula; comprovante de depósito → `409 RECEIPT_UNAVAILABLE`; classe `Money` | V7 |
| [12](../../docs/specs/12-idempotency.md) | Idempotência | Header `Idempotency-Key` obrigatório; chave gravada na mesma transação (`INSERT … ON CONFLICT DO NOTHING`); replay com `Idempotent-Replayed: true`; `422 IDEMPOTENCY_KEY_REUSED`; expira em 24 h; job de limpeza às 03:00 (São Paulo) | V8 |
| [13](../../docs/specs/13-audit.md) | Auditoria | `X-Request-Id`; `audit_events` append-only (trigger bloqueia UPDATE/DELETE); falhas gravadas em transação própria; Envers em `users`, `wallets`, `transactions` com `revinfo` (user + requestId) | V9 |
| [14](../../docs/specs/14-operation-limits.md) | Limites de operação | Tetos globais em `walletpay.limits.*`; limites do usuário (só pode reduzir); janela noturna 20h–6h (America/Sao_Paulo); `GET/PUT /wallets/me/limits`; `422 LIMIT_EXCEEDED` | V10 |
| [15](../../docs/specs/15-failure-handling.md) | Tratamento de falhas | `ErrorCode` único; `code` e `requestId` em todo erro; 401/403 em JSON; handler de `Exception` sem vazar detalhes; timeouts do CNPJ e `503 CNPJ_VALIDATION_UNAVAILABLE`; testes ponta a ponta | — |

## Tasks do plano (todas concluídas via commits)

1. Infra de testes de integração → 2. Catálogo de erros → 3. Request id, 401/403, timeout CNPJ → 4. `Money` →
5. Lock otimista (spec 10) → 6. Depósito (spec 11) → 7. Idempotência (spec 12) → 8. Auditoria A (spec 13) →
9. Envers B (spec 13) → 10. Limites aplicados (spec 14) → 11. Endpoints de limites → 12. Testes ponta a ponta (spec 15).

Linha de base antes da Task 1: 83 testes passando. Ao fim da Fase 2: 217 métodos `@Test` (contagem estática); após a Fase 3, 296 testes executados (ver [testes](../03-arquitetura/testes.md)).

## Correções posteriores à implementação

| Commit | Problema resolvido |
|---|---|
| `64647ae` | Deadlock em transferências cruzadas (A→B e B→A): `hibernate.order_updates=true` ordena os UPDATEs por chave primária |
| `9063138` | `LOGIN_FAILED` era perdido junto com o rollback do login: passou a ser gravado fora da transação |
| `7d14d51` | Valor bruto rejeitado era gravado inteiro nos metadados da auditoria: agora é truncado |
| `705af23` | Propriedade do Envers com prefixo errado era ignorada: documentado `org.hibernate.envers.*` |
| `75119f5` | Limites com precisão de sub-centavo e janela noturna vazia agora são rejeitados |
| `ea6df05` | Teste do controller que força erro 500 passou a explicar como é registrado |

## Divergências entre spec e implementação (decididas no plano)

O plano registra 11 decisões em que detalhou ou ajustou as specs — todas descritas em
[decisões técnicas](../06-decisoes/decisoes-tecnicas.md). As principais: manter os códigos de token das specs 04/05,
`BusinessException` com `ErrorCode`, `JdbcClient` para `idempotency_keys`/`audit_events`, relógio único (`Clock`),
`Money` rejeitando mais de 17 dígitos inteiros, e `OptimisticLockingFailureException` fora de saldo virando `409 DATA_CONFLICT`.

## Pendências de fechamento

- ✅ Merge de `feat/fases-1-2` e `docs/specs-fase-1-2` em `dev`. Falta `dev` → `main` (decisão D1, esteira E02).
- Marcar os checkboxes do plano ou arquivá-lo como concluído.
- Itens abertos de [pendências](../04-pendencias/pendencias-e-divida-tecnica.md).
