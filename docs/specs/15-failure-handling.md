# Failure Handling Spec

## Objetivo

Padronizar as respostas de erro da API, impedir vazamento de detalhes internos e validar o fluxo financeiro completo com testes de integracao.

## Contexto Atual

- Apenas `EMAIL_NOT_VERIFIED` preenche o campo `code` de `ApiErrorResponse`.
- Nao existe tratamento para `Exception` generica. Erros inesperados usam a resposta padrao do Spring.
- JSON malformado, metodo nao permitido e parametro com tipo errado tambem usam a resposta padrao do Spring.
- Respostas 401 e 403 do Spring Security nao usam `ApiErrorResponse`.
- `CnpjValidationUnavailableException` retorna 500, embora seja indisponibilidade de servico externo.
- O cliente HTTP de validacao de CNPJ (`HttpClientConfig`) nao define timeout.

## Escopo

Esta spec cobre:

- Catalogo unico de codigos de erro.
- `code` obrigatorio em toda resposta de erro.
- Campo `requestId` em toda resposta de erro.
- Tratamento de erros inesperados e erros de infraestrutura HTTP.
- Respostas 401 e 403 no formato `ApiErrorResponse`.
- Timeout e status 503 para validacao de CNPJ.
- Testes de integracao ponta a ponta.

Esta spec nao cobre:

- Migracao para RFC 9457 (`ProblemDetail`).
- Circuit breaker.
- Fila de mensagens nao processadas (Fase 3).
- Traducao de mensagens.
- Alteracao de mensagens existentes.

## Formato da Resposta de Erro

`ApiErrorResponse` ganha o campo `requestId`:

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

Regras:

- `code` nunca e nulo.
- `requestId` vem do MDC (spec 13).
- `fields` nunca e nulo. Sem detalhes, retorna objeto vazio.
- `message` nunca contem stack trace, nome de classe, SQL, nome de constraint ou nome de tabela.

## Catalogo de Erros

Enum `common/error/ErrorCode`, onde cada valor conhece seu status HTTP:

| Code | Status | Quando |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Falha de `@Valid` no DTO |
| `MALFORMED_REQUEST` | 400 | JSON malformado ou corpo ilegivel |
| `INVALID_PARAMETER` | 400 | Parametro ausente ou com tipo invalido |
| `INVALID_AMOUNT` | 400 | Valor monetario invalido (spec 11) |
| `INVALID_DOCUMENT` | 400 | CPF ou CNPJ invalido |
| `EMAIL_VERIFICATION_TOKEN_INVALID` | 400 | Token de verificacao invalido (spec 04) |
| `EMAIL_VERIFICATION_TOKEN_EXPIRED` | 400 | Token de verificacao expirado (spec 04) |
| `EMAIL_VERIFICATION_TOKEN_ALREADY_USED` | 400 | Token de verificacao ja utilizado (spec 04) |
| `PASSWORD_RESET_TOKEN_INVALID` | 400 | Token de redefinicao invalido (spec 05) |
| `PASSWORD_RESET_TOKEN_EXPIRED` | 400 | Token de redefinicao expirado (spec 05) |
| `PASSWORD_RESET_TOKEN_ALREADY_USED` | 400 | Token de redefinicao ja utilizado (spec 05) |
| `INVALID_PASSWORD_POLICY` | 400 | Senha fora da politica |
| `INVALID_TRANSFER` | 400 | Transferencia invalida, como para si mesmo |
| `INVALID_STATEMENT_QUERY` | 400 | Filtro de extrato invalido |
| `INVALID_LIMITS` | 400 | Limites invalidos (spec 14) |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | `Idempotency-Key` ausente (spec 12) |
| `IDEMPOTENCY_KEY_INVALID` | 400 | `Idempotency-Key` invalida (spec 12) |
| `UNAUTHORIZED` | 401 | JWT ausente, invalido ou expirado |
| `INVALID_CREDENTIALS` | 401 | E-mail ou senha incorretos |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh token invalido |
| `ACCESS_DENIED` | 403 | Acesso negado pelo Spring Security |
| `EMAIL_NOT_VERIFIED` | 403 | E-mail nao verificado |
| `RECEIPT_ACCESS_DENIED` | 403 | Comprovante de outro usuario |
| `ROUTE_NOT_FOUND` | 404 | Rota inexistente |
| `WALLET_NOT_FOUND` | 404 | Carteira nao encontrada |
| `TRANSACTION_NOT_FOUND` | 404 | Transacao nao encontrada |
| `METHOD_NOT_ALLOWED` | 405 | Metodo HTTP nao suportado na rota |
| `DUPLICATE_USER_DATA` | 409 | E-mail ou documento ja cadastrado |
| `RECEIPT_UNAVAILABLE` | 409 | Comprovante indisponivel |
| `BALANCE_CONFLICT` | 409 | Retentativas de concorrencia esgotadas (spec 10) |
| `DATA_CONFLICT` | 409 | Violacao de integridade nao tratada |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` nao suportado |
| `INSUFFICIENT_BALANCE` | 422 | Saldo insuficiente |
| `LIMIT_EXCEEDED` | 422 | Limite excedido (spec 14) |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Chave reutilizada com outro payload (spec 12) |
| `INTERNAL_ERROR` | 500 | Erro inesperado |
| `CNPJ_VALIDATION_UNAVAILABLE` | 503 | Servico de validacao de CNPJ indisponivel |

Os codes de token mantem o contrato das specs 04 e 05.

## Regras

### GlobalExceptionHandler

- Todo handler deve usar `ErrorCode` para definir `status`, `error` e `code`.
- Criar um metodo auxiliar unico que monta `ResponseEntity<ApiErrorResponse>` a partir de `ErrorCode`, mensagem e campos.
- Mensagens existentes devem ser preservadas.

Novos handlers:

- `HttpMessageNotReadableException` -> `MALFORMED_REQUEST`, mensagem `Corpo da requisicao invalido`.
- `MethodArgumentTypeMismatchException` e `MissingServletRequestParameterException` -> `INVALID_PARAMETER`, com o nome do parametro em `fields`.
- `MissingRequestHeaderException` -> `INVALID_PARAMETER`, exceto `Idempotency-Key`, que usa `IDEMPOTENCY_KEY_REQUIRED`.
- `HttpRequestMethodNotSupportedException` -> `METHOD_NOT_ALLOWED`.
- `HttpMediaTypeNotSupportedException` -> `UNSUPPORTED_MEDIA_TYPE`.
- `NoResourceFoundException` -> `ROUTE_NOT_FOUND`.
- `DataIntegrityViolationException` -> `DATA_CONFLICT`, mensagem `Conflito de dados`.
- `Exception` -> `INTERNAL_ERROR`, mensagem `Erro interno, informe o requestId ao suporte`.

### Erros Inesperados

- O handler de `Exception` registra log em nivel `ERROR` com stack trace e `requestId`.
- A resposta nunca contem a mensagem da excecao original.
- Erros de negocio conhecidos sao registrados em nivel `WARN` ou inferior, sem stack trace.

### Spring Security

- Criar `AuthenticationEntryPoint` que responde 401 `UNAUTHORIZED` no formato `ApiErrorResponse`.
- Criar `AccessDeniedHandler` que responde 403 `ACCESS_DENIED` no formato `ApiErrorResponse`.
- Registrar ambos em `SecurityConfig`, inclusive no resource server JWT.
- O header `WWW-Authenticate` deve continuar presente nas respostas 401.

### Validacao de CNPJ

- `HttpClientConfig` define timeout de conexao de 2 segundos e de leitura de 3 segundos para o cliente de CNPJ.
- Timeout ou erro de conexao lancam `CnpjValidationUnavailableException`.
- `CnpjValidationUnavailableException` passa de 500 para 503 `CNPJ_VALIDATION_UNAVAILABLE`.

## Testes de Integracao

Usam `AbstractIntegrationTest` (spec 10).

Para capturar o token de verificacao de e-mail e o token de redefinicao de senha, os testes registram implementacoes de teste de `EmailVerificationSender` e `PasswordRecoverySender` que guardam o ultimo token em memoria.

### Fluxo Feliz Completo

1. Cadastrar dois usuarios.
2. Confirmar o e-mail dos dois com o token capturado.
3. Fazer login dos dois.
4. Depositar `500.00` para o primeiro usuario com `Idempotency-Key`.
5. Transferir `120.00` do primeiro para o segundo com `Idempotency-Key`.
6. Consultar o saldo dos dois.
7. Consultar o extrato dos dois.
8. Consultar o comprovante da transferencia.
9. Validar `audit_events` das acoes executadas.
10. Validar revisoes em `wallets_aud` e `transactions_aud`.
11. Validar que todas as respostas contem `X-Request-Id`.

### Caminhos de Erro

- Requisicao sem JWT retorna 401 `UNAUTHORIZED` no formato `ApiErrorResponse`.
- Transferencia sem saldo retorna 422 `INSUFFICIENT_BALANCE` e gera `TRANSFER_REJECTED`.
- Transferencia acima do limite retorna 422 `LIMIT_EXCEEDED`.
- Chave idempotente reutilizada com outro payload retorna 422 `IDEMPOTENCY_KEY_REUSED`.
- JSON malformado retorna 400 `MALFORMED_REQUEST`.
- Rota inexistente retorna 404 `ROUTE_NOT_FOUND`.
- Erro inesperado forcado em teste retorna 500 `INTERNAL_ERROR` sem detalhes internos e com `requestId`.

## Criterios de Aceite

- Toda resposta de erro deve conter `code` nao nulo.
- Toda resposta de erro deve conter `requestId` igual ao header `X-Request-Id`.
- Todos os codes do catalogo devem existir em `ErrorCode`.
- Cada excecao de negocio existente deve retornar o code do catalogo.
- Mensagens existentes nao devem mudar.
- Erro inesperado deve retornar 500 `INTERNAL_ERROR` sem mensagem, classe ou stack trace da excecao.
- `DataIntegrityViolationException` nao deve expor nome de constraint.
- 401 e 403 do Spring Security devem usar `ApiErrorResponse`.
- Validacao de CNPJ indisponivel deve retornar 503.
- O cliente de CNPJ deve ter timeouts configurados.
- O fluxo feliz completo deve passar em teste de integracao.
- Os caminhos de erro listados devem passar em teste de integracao.
- Os testes existentes devem ser atualizados para os novos codes e continuar passando.
