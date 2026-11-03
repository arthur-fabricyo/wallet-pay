# Operation Limits Spec

## Objetivo

Limitar os valores movimentados por operacao, por periodo e no horario noturno, com tetos globais do sistema e limites menores configurados pelo proprio usuario.

## Escopo

Esta spec cobre:

- Limites globais configurados na aplicacao.
- Limites de transferencia configurados pelo usuario.
- Limite noturno de transferencia.
- Limites de deposito simulado.
- Consulta de limites e valores utilizados.
- Auditoria de alteracao e estouro de limites.

Esta spec nao cobre:

- Carencia para aumento de limite.
- Limites por destinatario.
- Limites por categoria.
- Aprovacao de aumento de limite.
- Limites de regras de automacao (Fase 4).
- Rate limiting de requisicoes.

## Limites Globais

`@ConfigurationProperties(prefix = "walletpay.limits")` em `wallet/config/LimitsProperties`, validado com `@Validated`.

```properties
walletpay.limits.timezone=America/Sao_Paulo
walletpay.limits.night.start=20:00
walletpay.limits.night.end=06:00

walletpay.limits.transfer.per-operation=5000.00
walletpay.limits.transfer.daily=10000.00
walletpay.limits.transfer.monthly=50000.00
walletpay.limits.transfer.night-per-operation=1000.00
walletpay.limits.transfer.night-window=1000.00

walletpay.limits.deposit.per-operation=10000.00
walletpay.limits.deposit.daily=20000.00
```

Validacoes na inicializacao:

- Todos os valores devem ser positivos.
- `per-operation <= daily <= monthly`.
- `night-per-operation <= night-window`.
- `night-per-operation <= per-operation`.

## Entidade

### WalletLimit

Limites de transferencia configurados pelo usuario.

Campos:

- `walletId`: UUID, chave primaria e estrangeira para `wallets`.
- `transferPerOperation`: valor ou nulo.
- `transferDaily`: valor ou nulo.
- `transferMonthly`: valor ou nulo.
- `transferNightPerOperation`: valor ou nulo.
- `transferNightWindow`: valor ou nulo.
- `version`: controle otimista.
- `updatedAt`: data da ultima alteracao.

Valor nulo significa usar o limite global.
A entidade usa `@Version` e `@Audited` (spec 13).

## Banco de Dados

### Migration V10__create_wallet_limits.sql

```sql
CREATE TABLE wallet_limits (
    wallet_id UUID PRIMARY KEY,
    transfer_per_operation NUMERIC(19, 2),
    transfer_daily NUMERIC(19, 2),
    transfer_monthly NUMERIC(19, 2),
    transfer_night_per_operation NUMERIC(19, 2),
    transfer_night_window NUMERIC(19, 2),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wallet_limits_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE
);

CREATE TABLE wallet_limits_aud (
    wallet_id UUID NOT NULL,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    transfer_per_operation NUMERIC(19, 2),
    transfer_daily NUMERIC(19, 2),
    transfer_monthly NUMERIC(19, 2),
    transfer_night_per_operation NUMERIC(19, 2),
    transfer_night_window NUMERIC(19, 2),
    version BIGINT,
    updated_at TIMESTAMPTZ,
    PRIMARY KEY (wallet_id, rev),
    CONSTRAINT fk_wallet_limits_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (id)
);

CREATE INDEX idx_transactions_sender_created ON transactions (sender_wallet_id, created_at);
CREATE INDEX idx_transactions_receiver_created ON transactions (receiver_wallet_id, created_at);
```

## Regras de Negocio

### Limite Efetivo

- Limite efetivo de transferencia = `min(global, usuario)`, quando o usuario tiver limite proprio.
- Sem limite proprio, o limite efetivo e o global.
- Depositos usam apenas os limites globais.

### Periodos

Todos os periodos usam o fuso `walletpay.limits.timezone`.

- Dia: dia do calendario corrente, de 00:00 ate o instante atual.
- Mes: mes do calendario corrente, do dia 1 as 00:00 ate o instante atual.
- Janela noturna: intervalo entre `night.start` e `night.end` que contem o instante atual.
  - As 23:00, a janela vai de hoje 20:00 ate amanha 06:00.
  - As 02:00, a janela vai de ontem 20:00 ate hoje 06:00.
  - Fora da janela, os limites noturnos nao se aplicam.

### Valor Utilizado

- Transferencia: soma de `amount` das transacoes `TRANSFER` com status `COMPLETED`, onde a carteira e a origem, com `created_at` dentro do periodo.
- Deposito: soma de `amount` das transacoes `DEPOSIT` com status `COMPLETED`, onde a carteira e o destino, com `created_at` dentro do periodo.
- A soma e feita por query agregada em `TransactionRepository`.

### Validacao da Transferencia

Ordem de verificacao, dentro de `CreateTransferUseCase`, depois de carregar as carteiras e antes de validar o saldo:

1. Valor por operacao (`PER_OPERATION`).
2. Se dentro da janela noturna, valor noturno por operacao (`NIGHT_PER_OPERATION`).
3. Utilizado no dia + valor (`DAILY`).
4. Utilizado no mes + valor (`MONTHLY`).
5. Se dentro da janela noturna, utilizado na janela + valor (`NIGHT_WINDOW`).

O limite noturno se soma aos outros: a noite, a transferencia precisa respeitar o limite noturno e tambem os limites diario e mensal.

### Validacao do Deposito

Dentro de `CreateDepositUseCase`, depois de carregar a carteira:

1. Valor por operacao (`PER_OPERATION`).
2. Utilizado no dia + valor (`DAILY`).

### Concorrencia

- Toda transferencia altera a versao da carteira de origem e todo deposito altera a versao da carteira de destino.
- Duas operacoes simultaneas na mesma carteira geram conflito de versao, e a retentativa da spec 10 recalcula o valor utilizado incluindo a operacao concluida.
- Por isso a validacao de limites nao precisa de lock adicional.

### Relogio

- A aplicacao expoe um bean `java.time.Clock` em `config/ClockConfig`.
- O calculo de periodos usa esse bean, para que os testes possam fixar o horario.

### Alteracao de Limites pelo Usuario

- O usuario pode definir limites de transferencia menores ou iguais ao teto global.
- Reducao e aumento valem imediatamente.
- Enviar `null` remove o limite proprio e volta ao global.
- Os limites efetivos resultantes devem ser coerentes:
  - `perOperation <= daily <= monthly`;
  - `nightPerOperation <= nightWindow`;
  - `nightPerOperation <= perOperation`.

## Endpoints

### GET /wallets/me/limits

Retorna os limites efetivos, os tetos globais, os limites do usuario e os valores utilizados.

#### Headers

```http
Authorization: Bearer jwt-token
```

#### Response 200

```json
{
  "transfer": {
    "perOperation": { "effective": "2000.00", "global": "5000.00", "custom": "2000.00" },
    "daily": { "effective": "10000.00", "global": "10000.00", "custom": null, "used": "350.00", "available": "9650.00" },
    "monthly": { "effective": "50000.00", "global": "50000.00", "custom": null, "used": "1200.00", "available": "48800.00" },
    "nightPerOperation": { "effective": "1000.00", "global": "1000.00", "custom": null },
    "nightWindow": { "effective": "1000.00", "global": "1000.00", "custom": null, "used": "0.00", "available": "1000.00" }
  },
  "deposit": {
    "perOperation": { "effective": "10000.00", "global": "10000.00", "custom": null },
    "daily": { "effective": "20000.00", "global": "20000.00", "custom": null, "used": "150.00", "available": "19850.00" }
  },
  "night": {
    "start": "20:00",
    "end": "06:00",
    "timezone": "America/Sao_Paulo",
    "active": false
  }
}
```

- Fora da janela noturna, `nightWindow.used` e `"0.00"` e `night.active` e `false`.
- `available` nunca e negativo.

### PUT /wallets/me/limits

Substitui todos os limites de transferencia do usuario.

#### Headers

```http
Authorization: Bearer jwt-token
```

#### Request

```json
{
  "transferPerOperation": "2000.00",
  "transferDaily": null,
  "transferMonthly": null,
  "transferNightPerOperation": null,
  "transferNightWindow": null
}
```

- Campo ausente equivale a `null`.
- Cada valor segue as regras de `Money.parse` (spec 11).

#### Response 200

Mesmo corpo de `GET /wallets/me/limits`, ja com os novos valores.

DTOs em `wallet/dto`: `WalletLimitsResponseDTO` e `UpdateWalletLimitsRequestDTO`.

## Auditoria

Novas acoes em `AuditAction` (spec 13):

| Acao | Recurso | Momento |
|---|---|---|
| `LIMITS_UPDATED` | WALLET | alteracao de limites pelo usuario |

- `LIMITS_UPDATED` registra em `metadata` os limites proprios anteriores e novos.
- Transferencia ou deposito recusado por limite gera `TRANSFER_REJECTED` ou `DEPOSIT_REJECTED` com `failure_code = LIMIT_EXCEEDED` e `metadata.limitType`.

## Erros

### 400 Bad Request

Quando, em `PUT /wallets/me/limits`:

- Valor invalido (`INVALID_AMOUNT`).
- Valor maior que o teto global (`INVALID_LIMITS`).
- Limites efetivos incoerentes (`INVALID_LIMITS`).

### 401 Unauthorized

Quando:

- JWT nao for enviado, for invalido ou estiver expirado.

### 404 Not Found

Quando:

- Carteira do usuario autenticado nao existir.

### 422 Unprocessable Entity

Quando uma transferencia ou deposito ultrapassar um limite.

Response:

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "LIMIT_EXCEEDED",
  "message": "Limite diario de transferencia excedido",
  "fields": {
    "limitType": "DAILY",
    "limit": "10000.00",
    "available": "650.00"
  }
}
```

`LimitExceededException` fica em `wallet/service`.

## Fluxo Principal: Transferencia

1. `TransferService` chama `CreateTransferUseCase`.
2. Validar valor e carregar carteiras.
3. Calcular limites efetivos da carteira de origem.
4. Calcular periodos com o `Clock`.
5. Somar valores utilizados nos periodos necessarios.
6. Validar limites na ordem definida.
7. Validar saldo.
8. Executar a transferencia.
9. Em caso de `LimitExceededException`, `TransferService` registra `TRANSFER_REJECTED` e relanca.

## Criterios de Aceite

- Deve recusar transferencia acima do limite por operacao.
- Deve recusar transferencia que ultrapasse o limite diario.
- Deve recusar transferencia que ultrapasse o limite mensal.
- Deve recusar transferencia noturna acima do limite noturno por operacao.
- Deve recusar transferencia noturna que ultrapasse o total da janela.
- Deve aplicar limites noturnos as 20:00 e as 05:59, e nao aplicar as 06:00 e as 19:59.
- Deve considerar transferencias feitas apos as 20:00 do dia anterior na janela das 02:00.
- Deve aplicar o limite do usuario quando menor que o global.
- Deve rejeitar limite do usuario maior que o global.
- Deve rejeitar limites incoerentes.
- `null` deve voltar ao limite global.
- Deve recusar deposito acima dos limites globais de deposito.
- Deve retornar 422 `LIMIT_EXCEEDED` com `limitType`, `limit` e `available`.
- `GET /wallets/me/limits` deve retornar valores utilizados corretos.
- Transferencias recusadas, de outros tipos ou de outras carteiras nao devem entrar no valor utilizado.
- Alteracao de limites deve gerar `LIMITS_UPDATED` e revisao em `wallet_limits_aud`.
- Teste de integracao: duas transferencias simultaneas que juntas ultrapassam o limite diario devem resultar em uma aceita e uma recusada.
- Testes de periodo devem usar `Clock` fixo.
