# Tratamento de erros

Spec: [15](../../docs/specs/15-failure-handling.md). Implementação: `common/error/` (`ErrorCode`, `BusinessException`,
`GlobalExceptionHandler`, `ApiErrorResponse`, `ApiErrorWriter`, `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler`).

## Regras

- Todo erro tem `code` (nunca nulo), `requestId` (igual ao header `X-Request-Id`) e `fields` (objeto, nunca nulo).
- `message` nunca contém stack trace, classe, SQL, constraint ou tabela.
- Exceção de negócio estende `BusinessException` (carrega o `ErrorCode`); o `GlobalExceptionHandler` tem um único handler para elas.
- Erro inesperado → `500 INTERNAL_ERROR` com mensagem genérica; o detalhe vai ao log em `ERROR` com `requestId`.
- Erro de negócio conhecido é logado em `WARN` ou menos, sem stack trace.
- 401/403 do Spring Security usam o mesmo formato (`UNAUTHORIZED`, `ACCESS_DENIED`), preservando `WWW-Authenticate`.

## Catálogo por status

| Status | Codes |
|---|---|
| 400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `INVALID_AMOUNT`, `INVALID_DOCUMENT`, `INVALID_PASSWORD_POLICY`, `INVALID_TRANSFER`, `INVALID_STATEMENT_QUERY`, `INVALID_LIMITS`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_INVALID`, `EMAIL_VERIFICATION_TOKEN_INVALID`/`_EXPIRED`/`_ALREADY_USED`, `PASSWORD_RESET_TOKEN_INVALID`/`_EXPIRED`/`_ALREADY_USED` |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 403 | `ACCESS_DENIED`, `EMAIL_NOT_VERIFIED`, `RECEIPT_ACCESS_DENIED` |
| 404 | `ROUTE_NOT_FOUND`, `WALLET_NOT_FOUND`, `TRANSACTION_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `DUPLICATE_USER_DATA`, `RECEIPT_UNAVAILABLE`, `BALANCE_CONFLICT`, `DATA_CONFLICT` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 422 | `INSUFFICIENT_BALANCE`, `LIMIT_EXCEEDED`, `IDEMPOTENCY_KEY_REUSED` |
| 500 | `INTERNAL_ERROR` |
| 503 | `CNPJ_VALIDATION_UNAVAILABLE` |

## Casos que merecem atenção

- `LIMIT_EXCEEDED` traz em `fields`: `limitType` (`PER_OPERATION`, `NIGHT_PER_OPERATION`, `DAILY`, `MONTHLY`, `NIGHT_WINDOW`), `limit` e `available`.
- `BALANCE_CONFLICT`: o cliente deve repetir a chamada (com a mesma `Idempotency-Key`).
- `DATA_CONFLICT` também cobre conflito de versão fora de operação de saldo (ex.: dois `PUT /wallets/me/limits` simultâneos).
- Valor gigante (mais de 17 dígitos inteiros) → `400 INVALID_AMOUNT`, nunca estouro de `NUMERIC(19,2)`.
- Os códigos de token mantêm o contrato das specs 04/05 (a spec 15 original usava nomes diferentes; o plano corrigiu a spec).

## Como adicionar um novo erro

1. Criar o valor em `ErrorCode` com o status HTTP.
2. Lançar uma exceção que estenda `BusinessException`.
3. Cobrir em `ErrorCodeTest`/`GlobalExceptionHandlerTest` e, se for operação de saldo, auditar em `*_REJECTED`.
