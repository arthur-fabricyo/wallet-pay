# Idempotency Spec

## Objetivo

Impedir que uma operacao financeira seja executada mais de uma vez quando o cliente repete a mesma requisicao, por exemplo apos timeout ou queda de conexao.

## Escopo

Esta spec cobre:

- Header `Idempotency-Key` em transferencias e depositos.
- Armazenamento das chaves no PostgreSQL.
- Reenvio da resposta original para requisicoes repetidas.
- Deteccao de reuso da chave com payload diferente.
- Requisicoes simultaneas com a mesma chave.
- Expiracao e limpeza das chaves.

Esta spec nao cobre:

- Idempotencia em endpoints de leitura.
- Idempotencia em cadastro, login ou recuperacao de senha.
- Armazenamento em Redis.
- Idempotencia de webhooks (Fase 6).
- Idempotencia de execucao de regras de automacao (Fase 4).

## Decisao Tecnica

As chaves ficam em uma tabela do PostgreSQL e sao gravadas na mesma transacao da operacao financeira.

Motivo:

- Chave, debito, credito e transacao sao confirmados ou desfeitos juntos.
- Com Redis, um crash entre o commit do banco e a gravacao da chave permitiria cobranca duplicada.
- Com Redis, um rollback do banco apos gravar a chave deixaria uma chave marcada sem operacao.

## Entidade

### IdempotencyKey

Campos:

- `id`: UUID.
- `userId`: UUID do usuario dono da chave.
- `idempotencyKey`: valor enviado pelo cliente.
- `operation`: operacao protegida.
- `requestHash`: SHA-256 em hexadecimal do comando normalizado.
- `responseStatus`: status HTTP da resposta original.
- `responseBody`: corpo da resposta original em JSON.
- `createdAt`: data de criacao.
- `expiresAt`: data de expiracao.

Pacote: `common/idempotency`.

## Enums

### IdempotentOperation

Valores:

- `TRANSFER_CREATE`.
- `DEPOSIT_CREATE`.

## Banco de Dados

### Migration V8__create_idempotency_keys.sql

```sql
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_idempotency_keys_user_operation_key UNIQUE (user_id, operation, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
```

## Regras de Negocio

### Header

- `Idempotency-Key` e obrigatorio em `POST /transactions/transfers` e `POST /wallets/me/deposits`.
- Tamanho entre 1 e 100 caracteres.
- Caracteres permitidos: `A-Z`, `a-z`, `0-9`, `_` e `-`.
- Recomendacao ao cliente: usar um UUID novo por operacao.
- A chave e unica por usuario e operacao. Usuarios diferentes podem usar a mesma chave.

### Hash do Payload

- O hash e calculado sobre o comando normalizado, nao sobre o JSON bruto.
- Transferencia: `receiverUserId`, `amount` normalizado com `Money` e `description` normalizada.
- Deposito: `amount` normalizado com `Money` e `description` normalizada.
- Campos sao concatenados em ordem fixa, separados por `|`, com valor nulo representado por string vazia.
- Assim `"10.0"` e `"10.00"` geram o mesmo hash.

### Execucao

Dentro da transacao do use case interno (camada `@Transactional` da spec 10):

1. Executar `INSERT ... ON CONFLICT (user_id, operation, idempotency_key) DO NOTHING`.
2. Se outra transacao estiver usando a mesma chave, o PostgreSQL bloqueia este comando ate ela terminar.
3. Se uma linha foi inserida, a chave e nova:
   - executar a operacao;
   - gravar `response_status` e `response_body` na linha;
   - confirmar tudo na mesma transacao.
4. Se nenhuma linha foi inserida, a chave ja existe:
   - ler a linha existente;
   - se `request_hash` for igual, retornar a resposta salva sem executar a operacao;
   - se `request_hash` for diferente, lancar `IdempotencyKeyReusedException`.

Consequencias:

- Apenas respostas de sucesso sao salvas.
- Erro de negocio desfaz a transacao e remove a chave. O cliente pode tentar de novo com a mesma chave.
- Cada retentativa da spec 10 abre nova transacao. O rollback anterior ja removeu a chave.
- Duas requisicoes simultaneas com a mesma chave nunca executam a operacao duas vezes.
- Nao existe estado "em processamento" nem resposta 409 para chave em uso.

### Resposta Repetida

- A resposta repetida tem o mesmo status e o mesmo corpo da original.
- A resposta repetida inclui o header `Idempotent-Replayed: true`.
- O corpo e salvo serializando o result do use case (`TransferResult` ou `DepositResult`) com o mapper JSON da aplicacao.
- Na repeticao, o result e desserializado e convertido para o DTO de resposta pelo controller.

### Expiracao e Limpeza

- `expires_at = created_at + 24 horas`.
- Chave expirada e tratada como inexistente: o `INSERT` deve considerar a linha expirada como removivel.
- Implementacao: antes do `INSERT`, apagar a linha da mesma chave se `expires_at < now()`.
- Job `IdempotencyKeyCleanupJob` com `@Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")`.
- O job apaga chaves expiradas em lotes de 1000 ate nao restar nenhuma.
- `@EnableScheduling` em `config/SchedulingConfig.java`.

## Endpoints

### POST /transactions/transfers

#### Headers

```http
Authorization: Bearer jwt-token
Idempotency-Key: 0b9f3c1e-5a52-4c8b-9a51-1f3d2e7c0001
```

Request e response inalterados.

### POST /wallets/me/deposits

#### Headers

```http
Authorization: Bearer jwt-token
Idempotency-Key: 0b9f3c1e-5a52-4c8b-9a51-1f3d2e7c0002
```

Request e response inalterados.

## Erros

### 400 Bad Request

Quando:

- `Idempotency-Key` nao for enviado (`IDEMPOTENCY_KEY_REQUIRED`).
- `Idempotency-Key` tiver tamanho ou caracteres invalidos (`IDEMPOTENCY_KEY_INVALID`).

### 422 Unprocessable Entity

Quando:

- A mesma chave for usada com payload diferente (`IDEMPOTENCY_KEY_REUSED`).

Response:

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "Idempotency-Key ja utilizada com outro payload",
  "fields": {}
}
```

## Fluxo Principal

1. Controller le e valida `Idempotency-Key`.
2. Controller monta o comando com a chave e chama a camada externa (`TransferService` ou `DepositService`).
3. A camada `@Transactional` calcula o hash do comando normalizado.
4. Remove a chave expirada, se existir.
5. Tenta inserir a chave.
6. Se a chave ja existia com mesmo hash, retorna a resposta salva.
7. Se a chave ja existia com hash diferente, lanca erro 422.
8. Se a chave e nova, executa a operacao e grava a resposta.
9. Confirma tudo na mesma transacao.
10. Controller responde, incluindo `Idempotent-Replayed: true` quando for repeticao.

## Criterios de Aceite

- Deve exigir `Idempotency-Key` em transferencias e depositos.
- Deve rejeitar chave ausente com 400 `IDEMPOTENCY_KEY_REQUIRED`.
- Deve rejeitar chave invalida com 400 `IDEMPOTENCY_KEY_INVALID`.
- Repetir a mesma chave e o mesmo payload deve retornar a mesma resposta e debitar uma unica vez.
- A resposta repetida deve conter `Idempotent-Replayed: true`.
- Repetir a mesma chave com payload diferente deve retornar 422 `IDEMPOTENCY_KEY_REUSED`.
- `"10.0"` e `"10.00"` devem ser tratados como o mesmo payload.
- Usuarios diferentes devem poder usar a mesma chave.
- Uma operacao recusada por regra de negocio nao deve deixar chave gravada.
- Teste de integracao: 10 requisicoes simultaneas com a mesma chave devem gerar exatamente 1 transacao.
- Chave expirada deve permitir nova execucao.
- O job de limpeza deve apagar apenas chaves expiradas.
- Os testes existentes de transferencia e deposito devem ser atualizados para enviar o header.
