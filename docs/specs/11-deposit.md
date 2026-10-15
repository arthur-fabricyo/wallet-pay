# Deposit Spec

## Objetivo

Permitir que o usuario adicione saldo a propria carteira por meio de um deposito simulado, sem integracao com banco ou provedor de pagamento.

## Contexto

Hoje nao existe forma de colocar dinheiro em uma carteira.
Sem entrada de saldo nao e possivel demonstrar transferencias, extrato ou futuras automacoes.
Na Fase 6, o Pix simulado sera outra origem de transacoes `DEPOSIT`.

## Escopo

Esta spec cobre:

- Deposito simulado na propria carteira.
- Registro da transacao `DEPOSIT`.
- Exibicao do deposito no extrato.
- Comportamento do comprovante para depositos.
- Extracao da validacao de valores monetarios para uma classe reutilizavel.

Esta spec nao cobre:

- Saque.
- Estorno.
- Deposito via Pix, boleto ou banco real.
- Deposito na carteira de outro usuario.
- Comprovante de deposito.
- Idempotencia (spec 12).
- Limites de deposito (spec 14).

## Entidade

### Transaction

Alteracoes:

- `senderWalletId` passa a ser opcional.
- Para `DEPOSIT`, `senderWalletId` e nulo e `receiverWalletId` e a carteira do usuario autenticado.
- Para `TRANSFER`, `senderWalletId` continua obrigatorio.

Nova factory em `TransactionEntity`:

```java
public static TransactionEntity completedDeposit(WalletEntity receiverWallet, BigDecimal amount, String description)
```

## Enums

### TransactionType

Valores aceitos no banco:

- `TRANSFER`.
- `DEPOSIT`.

## Banco de Dados

### Migration V7__allow_deposit_transactions.sql

```sql
ALTER TABLE transactions ALTER COLUMN sender_wallet_id DROP NOT NULL;

ALTER TABLE transactions DROP CONSTRAINT ck_transactions_type;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type
    CHECK (type IN ('TRANSFER', 'DEPOSIT'));

ALTER TABLE transactions ADD CONSTRAINT ck_transactions_transfer_has_sender
    CHECK (type <> 'TRANSFER' OR sender_wallet_id IS NOT NULL);

ALTER TABLE transactions ADD CONSTRAINT ck_transactions_deposit_has_no_sender
    CHECK (type <> 'DEPOSIT' OR sender_wallet_id IS NULL);
```

## Valores Monetarios

Criar `common/money/Money.java` com a logica hoje privada em `CreateTransferUseCase`:

- `Money.parse(String raw)`: valida e converte para `BigDecimal` com escala 2.
- `Money.format(BigDecimal amount)`: converte para string com duas casas.

Regras de `Money.parse`:

- Valor obrigatorio.
- Valor numerico.
- Maior que zero.
- No maximo duas casas decimais.

Falhas lancam `InvalidAmountException` (em `common/money`), mapeada para 400 com code `INVALID_AMOUNT`.

`CreateTransferUseCase` e `CreateDepositUseCase` devem usar `Money`.
A mensagem de erro da transferencia para valor invalido continua a mesma.

## Regras de Negocio

- O deposito exige JWT valido.
- O deposito sempre credita a carteira do usuario autenticado.
- O valor segue as regras de `Money.parse`.
- A moeda deve ser `BRL`.
- A descricao e opcional, com no maximo 255 caracteres, e e normalizada como na transferencia.
- Credito e registro da transacao devem ocorrer na mesma transacao de banco.
- O deposito segue a estrutura em duas camadas da spec 10:
  - `DepositService` (`balanceRetryTemplate`, sem transacao) em `wallet/service`.
  - `CreateDepositUseCase` (`@Transactional`) em `wallet/service`.
- A transacao e criada com status `COMPLETED`.
- Valores monetarios sao retornados como string.

## Endpoints

### POST /wallets/me/deposits

Cria um deposito simulado na carteira do usuario autenticado.

#### Headers

```http
Authorization: Bearer jwt-token
```

#### Request

```json
{
  "amount": "150.00",
  "description": "Aporte inicial"
}
```

DTO: `wallet/dto/DepositRequestDTO.java`.

#### Response 201

```json
{
  "transactionId": "18f02b68-d4f9-4d35-8b65-75dd39f60002",
  "type": "DEPOSIT",
  "status": "COMPLETED",
  "amount": "150.00",
  "currency": "BRL",
  "description": "Aporte inicial",
  "wallet": {
    "id": "5f12d35f-77c5-4f6f-b1f2-b29eddf96001",
    "balance": "250.00",
    "currency": "BRL",
    "updatedAt": "2026-10-15T10:30:00Z"
  },
  "createdAt": "2026-10-15T10:30:00Z",
  "completedAt": "2026-10-15T10:30:00Z"
}
```

DTO: `wallet/dto/DepositResponseDTO.java`.

## Ajustes em Funcionalidades Existentes

### Extrato (spec 08)

`GetWalletStatementUseCase` hoje assume que toda transacao tem carteira de origem e falharia com `NullPointerException` em um deposito.

Novas regras:

- `DEPOSIT` aparece com direcao `IN`.
- `DEPOSIT` aparece com `counterpartyName` nulo.
- O filtro `type=DEPOSIT` deve funcionar.
- A busca de transacoes da carteira deve incluir depositos onde a carteira e o destino.

### Comprovante (spec 07)

- O comprovante continua disponivel apenas para `TRANSFER`.
- Para `DEPOSIT`, retornar 409 com code `RECEIPT_UNAVAILABLE`, reaproveitando `ReceiptUnavailableException`.
- Mensagem: `Comprovante disponivel apenas para transferencias`.
- O controle de acesso do comprovante nao pode assumir `senderWallet` nao nulo.

## Erros

### 400 Bad Request

Quando:

- Valor ausente, nao numerico, menor ou igual a zero ou com mais de duas casas decimais (`INVALID_AMOUNT`).
- Descricao com mais de 255 caracteres.
- Payload invalido.

### 401 Unauthorized

Quando:

- JWT nao for enviado.
- JWT estiver invalido.
- JWT estiver expirado.

### 404 Not Found

Quando:

- Carteira do usuario autenticado nao existir.

### 409 Conflict

Quando:

- Retentativas de concorrencia se esgotarem (`BALANCE_CONFLICT`, spec 10).

## Fluxo Principal

1. Receber requisicao autenticada.
2. Identificar usuario pelo JWT.
3. Validar payload.
4. `DepositService` chama `CreateDepositUseCase` por meio de `balanceRetryTemplate.invoke`.
5. Validar e normalizar o valor com `Money.parse`.
6. Buscar a carteira do usuario.
7. Creditar o valor na carteira.
8. Criar transacao `DEPOSIT` com status `COMPLETED`.
9. Confirmar alteracoes na mesma transacao.
10. Retornar dados da transacao e da carteira atualizada.

## Criterios de Aceite

- Deve creditar a carteira do usuario autenticado.
- Deve registrar transacao `DEPOSIT` com `sender_wallet_id` nulo.
- Deve retornar 201 com saldo atualizado.
- Deve retornar valores monetarios como string.
- Deve rejeitar deposito sem JWT.
- Deve rejeitar valor invalido com 400 `INVALID_AMOUNT`.
- O banco deve rejeitar `TRANSFER` sem carteira de origem.
- O banco deve rejeitar `DEPOSIT` com carteira de origem.
- O extrato deve exibir o deposito com direcao `IN` e `counterpartyName` nulo.
- O extrato deve filtrar por `type=DEPOSIT`.
- O comprovante de deposito deve retornar 409 `RECEIPT_UNAVAILABLE`.
- Teste de integracao: depositos concorrentes na mesma carteira devem somar corretamente.
- Os testes existentes de transferencia, extrato e comprovante devem continuar passando.

## Observacao

Ate a implementacao das specs 12 e 14, este endpoint nao tem idempotencia nem limites.
