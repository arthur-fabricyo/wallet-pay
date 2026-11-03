# Modelo de dados

PostgreSQL 16, esquema controlado por Flyway (`src/main/resources/db/migration`), `ddl-auto=validate`.

## Migrations

| Versão | Arquivo | Conteúdo |
|---|---|---|
| V1 | `create_users_and_wallets` | `users` (inclui `document` único) e `wallets` |
| V2 | `create_refresh_tokens` | refresh tokens |
| V3 | `create_email_verification_tokens` | tokens de verificação de e-mail |
| V4 | `create_password_reset_tokens` | tokens de redefinição de senha |
| V5 | `create_transactions` | `transactions` |
| V6 | `add_wallet_version` | `wallets.version BIGINT` (lock otimista) |
| V7 | `allow_deposit_transactions` | `sender_wallet_id` anulável; tipo `DEPOSIT`; checks de coerência tipo × remetente |
| V8 | `create_idempotency_keys` | `idempotency_keys` (`response_body JSONB`, único por usuário+operação+chave) |
| V9 | `create_audit` | `audit_events` (append-only por trigger), `revinfo`, `users_aud`, `wallets_aud`, `transactions_aud` |
| V10 | `create_wallet_limits` | `wallet_limits`, `wallet_limits_aud`, índices em `transactions` por carteira+`created_at` |

## Tabelas de negócio

```text
users ──1:1── wallets ──1:0..1── wallet_limits
  │              │
  │              ├── transactions (sender_wallet_id? , receiver_wallet_id)
  │
  ├── refresh_tokens
  ├── email_verification_tokens
  ├── password_reset_tokens
  └── idempotency_keys
```

- **users:** `id`, `name`, `email`, `password_hash`, `document` (única), `email_verified`, `created_at`, `updated_at`.
- **wallets:** `id`, `user_id`, `balance NUMERIC(19,2)`, `currency` (BRL), `version`, timestamps.
- **transactions:** `id`, `sender_wallet_id` (nulo em `DEPOSIT`), `receiver_wallet_id`, `type` (`TRANSFER`/`DEPOSIT`),
  `status`, `amount`, `currency`, `description`, `created_at`, `completed_at`, `failed_at`, `failure_reason`.
  O enum Java tem `PENDING`/`COMPLETED`/`FAILED`, mas o check do banco (V5) só aceita `COMPLETED` e `FAILED`;
  hoje o código só cria transações `COMPLETED` (falhas ficam na auditoria, não em `transactions`).
- **wallet_limits:** uma linha por carteira; colunas anuláveis (nulo = usa o limite global); `version` para lock otimista.

## Tabelas de suporte

- **idempotency_keys:** `user_id`, `idempotency_key`, `operation` (`TRANSFER_CREATE`/`DEPOSIT_CREATE`), `request_hash`,
  `response_status`, `response_body`, `created_at`, `expires_at`. Limpeza diária de expiradas.
- **audit_events:** `occurred_at`, `actor_type` (`USER`/`SYSTEM`/`ANONYMOUS`), `actor_user_id`, `action`, `resource_type`,
  `resource_id`, `outcome` (`SUCCESS`/`FAILURE`), `failure_code`, `metadata JSONB`, `ip`, `request_id`.
  Trigger impede UPDATE/DELETE.
- **revinfo + `*_aud`:** histórico do Envers (`rev`, `revtype`); `revinfo` guarda `user_id` e `request_id`.
  `users_aud` não contém `password_hash`.

## Enums relevantes

- `AuditAction`: `USER_REGISTERED`, `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOGOUT`, `EMAIL_VERIFIED`, `PASSWORD_RESET_REQUESTED`,
  `PASSWORD_RESET_COMPLETED`, `TRANSFER_CREATED`, `TRANSFER_REJECTED`, `DEPOSIT_CREATED`, `DEPOSIT_REJECTED`, `LIMITS_UPDATED`.
- `AuditResourceType`: `USER`, `WALLET`, `TRANSACTION`, `SESSION`.
- `TransactionType`: `TRANSFER`, `DEPOSIT`. `TransactionStatus`: `PENDING`, `COMPLETED`, `FAILED`
(atenção: o check `ck_transactions_status` do banco não inclui `PENDING` — relevante para a Fase 6, Pix assíncrono).

## Nota operacional

Se o banco local do `docker-compose` já rodou uma migration que foi alterada depois, recriar o volume:
`docker compose down -v`. Os testes usam container novo e não são afetados.
