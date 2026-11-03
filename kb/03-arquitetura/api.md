# API HTTP

Documentação interativa (app rodando): `http://localhost:8080/swagger-ui.html`; OpenAPI em `/v3/api-docs`.

## Endpoints

| Método | Rota | Auth | Descrição | Spec |
|---|---|---|---|---|
| `POST` | `/users` | pública | Cadastrar usuário (cria carteira) | 01 |
| `POST` | `/auth/login` | pública | Login (exige e-mail verificado) | 02 |
| `POST` | `/auth/refresh` | pública | Renovar access token | 02 |
| `POST` | `/auth/logout` | JWT | Invalidar refresh token | 02 |
| `POST` | `/auth/email-verification/resend` | pública | Reenviar token de verificação | 04 |
| `POST` | `/auth/email-verification/confirm` | pública | Confirmar e-mail | 04 |
| `POST` | `/auth/password-recovery/request` | pública | Solicitar redefinição | 05 |
| `POST` | `/auth/password-recovery/reset` | pública | Redefinir senha | 05 |
| `GET` | `/wallets/me/balance` | JWT | Saldo | 03 |
| `GET` | `/wallets/me/statement` | JWT | Extrato (`page`, `size`, `from`, `to`, `type`, `sort`) | 08 |
| `POST` | `/wallets/me/deposits` | JWT + `Idempotency-Key` | Depósito simulado | 11, 12 |
| `GET` | `/wallets/me/limits` | JWT | Limites efetivos, tetos, personalizados e utilizados | 14 |
| `PUT` | `/wallets/me/limits` | JWT | Substituir limites personalizados (só reduzir) | 14 |
| `POST` | `/transactions/transfers` | JWT + `Idempotency-Key` | Transferência | 06, 12 |
| `GET` | `/transactions/{transactionId}/receipt` | JWT | Comprovante (só transferências; só participantes) | 07 |
| `GET` | `/actuator/health` | pública | Saúde da aplicação | — |

## Convenções de contrato

- Valores monetários: **string** com 2 casas (`"150.00"`). Moeda: `BRL`.
- Datas em ISO-8601 UTC.
- Header de requisição: `Authorization: Bearer <jwt>`; `Idempotency-Key` (1–100 chars `A-Za-z0-9_-`) nas operações de dinheiro;
  `X-Request-Id` opcional (1–64 chars).
- Headers de resposta: `X-Request-Id` sempre; `Idempotent-Replayed: true` quando a resposta é repetição.
- Extrato: depósito aparece como `IN` com `counterpartyName` nulo; filtro `type=DEPOSIT` funciona.
- Tokens: access JWT 20 min; refresh 7 dias.

## Formato de erro

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "INSUFFICIENT_BALANCE",
  "message": "Saldo insuficiente",
  "fields": {},
  "requestId": "0b9f3c1e-5a52-4c8b-9a51-1f3d2e7c0001"
}
```

Catálogo completo em [erros.md](erros.md).
