# Audit Spec

## Objetivo

Registrar de forma rastreavel quem fez cada acao relevante de negocio e seguranca, com qual resultado, e manter o historico de estado das entidades financeiras.

## Escopo

Esta spec cobre:

- Identificador de requisicao `X-Request-Id`.
- Eventos de auditoria de negocio e seguranca em `audit_events`.
- Historico de estado das entidades com Hibernate Envers.
- Registro de falhas mesmo quando a operacao e desfeita.
- Protecao contra alteracao e exclusao de eventos.

Esta spec nao cobre:

- Endpoint de consulta da auditoria. A consulta e feita por SQL.
- Politica de retencao ou expurgo.
- Exportacao de auditoria.
- Envio de eventos para sistemas externos.
- Auditoria de leituras, como consulta de saldo e extrato.

## Decisao Tecnica

Dois mecanismos complementares:

- `audit_events`: registra a intencao e o resultado das acoes. Captura tentativas que falharam, como login invalido ou transferencia recusada.
- Hibernate Envers: registra o estado de cada entidade auditada apos cada alteracao confirmada.

Versao: `hibernate-envers` 7.4.1, gerenciada pelo `spring-boot-starter-parent` 4.1.0.
O modulo Envers sera descontinuado no Hibernate 8, cujo novo `@Audited` do core le o formato de log do Envers.

## Request Id

- Filtro `common/web/RequestIdFilter`, executado antes do filtro de seguranca.
- Le o header `X-Request-Id`.
- Aceita valor com 1 a 64 caracteres `A-Z`, `a-z`, `0-9`, `_` e `-`.
- Se ausente ou invalido, gera um UUID.
- Coloca o valor no MDC com a chave `requestId`.
- Devolve o valor no header `X-Request-Id` da resposta.
- Remove o valor do MDC ao final da requisicao.
- O padrao de log deve incluir `requestId`.

## Parte A: audit_events

### Entidade AuditEvent

Campos:

- `id`: UUID.
- `occurredAt`: data do evento.
- `actorType`: tipo do autor.
- `actorUserId`: UUID do usuario autor, quando existir.
- `action`: acao executada.
- `resourceType`: tipo do recurso afetado.
- `resourceId`: id do recurso afetado, quando existir.
- `outcome`: resultado.
- `failureCode`: code do erro, quando `outcome = FAILURE`.
- `metadata`: dados adicionais em JSON.
- `ip`: IP de origem.
- `requestId`: valor de `X-Request-Id`.

Pacote: `audit`.

### Enums

`AuditActorType`:

- `USER`.
- `SYSTEM`.
- `ANONYMOUS`.

`AuditOutcome`:

- `SUCCESS`.
- `FAILURE`.

`AuditResourceType`:

- `USER`.
- `WALLET`.
- `TRANSACTION`.
- `SESSION`.

`AuditAction`:

| Acao | Recurso | Momento |
|---|---|---|
| `USER_REGISTERED` | USER | cadastro concluido |
| `LOGIN_SUCCEEDED` | SESSION | login com sucesso |
| `LOGIN_FAILED` | SESSION | credencial invalida ou e-mail nao verificado |
| `LOGOUT` | SESSION | logout |
| `EMAIL_VERIFIED` | USER | confirmacao de e-mail |
| `PASSWORD_RESET_REQUESTED` | USER | pedido de recuperacao, apenas quando o e-mail existe |
| `PASSWORD_RESET_COMPLETED` | USER | senha redefinida |
| `TRANSFER_CREATED` | TRANSACTION | transferencia concluida |
| `TRANSFER_REJECTED` | WALLET | transferencia recusada |
| `DEPOSIT_CREATED` | TRANSACTION | deposito concluido |
| `DEPOSIT_REJECTED` | WALLET | deposito recusado |

A spec 14 adiciona as acoes de limite.

### Banco de Dados

Migration V9__create_audit.sql, parte 1:

```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID,
    outcome VARCHAR(10) NOT NULL,
    failure_code VARCHAR(60),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip VARCHAR(45),
    request_id VARCHAR(64),
    CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT ck_audit_events_failure_code CHECK (outcome = 'FAILURE' OR failure_code IS NULL)
);

CREATE INDEX idx_audit_events_actor_occurred ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_events_resource ON audit_events (resource_type, resource_id);
CREATE INDEX idx_audit_events_request_id ON audit_events (request_id);

CREATE FUNCTION prevent_audit_events_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events e append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_events_change();
```

### AuditLogger

Servico `audit/AuditLogger` com dois metodos:

- `record(AuditEntry entry)`: `@Transactional(propagation = MANDATORY)`. Grava o evento na transacao da operacao. Usado para sucesso.
- `recordFailure(AuditEntry entry)`: `@Transactional(propagation = REQUIRES_NEW)`. Grava o evento em transacao propria. Usado para falha.

Preenchimento automatico:

- `occurredAt` pelo `Clock` da aplicacao.
- `actorType` e `actorUserId` pelo `SecurityContext`, quando nao informados.
- `ip` por `HttpServletRequest.getRemoteAddr()`. Headers como `X-Forwarded-For` nao sao confiados nesta fase.
- `requestId` pelo MDC.

### Regras

- Evento de sucesso e gravado na mesma transacao da operacao. Se a operacao for desfeita, o evento tambem e.
- Evento de falha e gravado em transacao propria. Se fosse gravado na transacao da operacao, o rollback apagaria o registro da falha.
- Para transferencia e deposito, a falha e registrada pela camada externa da spec 10 (`TransferService` e `DepositService`): captura a excecao de negocio, chama `recordFailure` e relanca a excecao.
- Para login, a falha e registrada por `AuthService`.
- Erros de validacao de DTO (`@Valid`) nao sao auditados.
- Resposta repetida por idempotencia (spec 12) nao gera novo evento.
- `metadata` nunca pode conter senha, hash de senha, token, refresh token ou documento completo.
- Documento, quando necessario, deve ser mascarado, por exemplo `***.456.789-**`.

### Metadata Por Acao

- `LOGIN_FAILED`: `email` informado e `reason` (`INVALID_CREDENTIALS` ou `EMAIL_NOT_VERIFIED`).
- `TRANSFER_CREATED`: `amount`, `currency`, `senderWalletId`, `receiverWalletId`.
- `TRANSFER_REJECTED`: `amount` informado, `receiverUserId` e `failureCode`.
- `DEPOSIT_CREATED`: `amount`, `currency`, `walletId`.
- `DEPOSIT_REJECTED`: `amount` informado e `failureCode`.

## Parte B: Hibernate Envers

### Dependencia

- `org.hibernate.orm:hibernate-envers`.

### Entidades Auditadas

- `UserEntity`, com `@NotAudited` em `passwordHash`.
- `WalletEntity`.
- `TransactionEntity`.

Nao sao auditadas:

- Tokens de refresh, verificacao de e-mail e redefinicao de senha.
- `idempotency_keys`.
- `audit_events`.

Relacoes de entidades auditadas para entidades nao auditadas devem usar `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)`.

### Revisao Customizada

Entidade `audit/AuditRevisionEntity` anotada com `@RevisionEntity(AuditRevisionListener.class)`, tabela `revinfo`:

- `id`: `BIGINT`, `@RevisionNumber`.
- `timestamp`: `BIGINT`, `@RevisionTimestamp`.
- `userId`: UUID do usuario autenticado, quando existir.
- `requestId`: valor do MDC.

`AuditRevisionListener` preenche `userId` pelo `SecurityContext` e `requestId` pelo MDC.

### Configuracao

```properties
spring.jpa.properties.hibernate.envers.audit_table_suffix=_aud
spring.jpa.properties.hibernate.envers.revision_field_name=rev
spring.jpa.properties.hibernate.envers.revision_type_field_name=revtype
```

### Banco de Dados

Migration V9__create_audit.sql, parte 2:

- Tabela `revinfo` com `id BIGSERIAL PRIMARY KEY`, `timestamp BIGINT NOT NULL`, `user_id UUID` e `request_id VARCHAR(64)`.
- Tabelas `users_aud`, `wallets_aud` e `transactions_aud`.
- Cada tabela `_aud` contem as colunas auditadas da entidade, mais `rev BIGINT NOT NULL` e `revtype SMALLINT`.
- Chave primaria de cada `_aud`: `(id, rev)`.
- `rev` com chave estrangeira para `revinfo(id)`.
- `users_aud` nao contem `password_hash`.
- `wallets_aud` contem `version` e `balance`.
- As colunas devem bater com o mapeamento, porque o projeto usa `spring.jpa.hibernate.ddl-auto=validate`.

### Regras

- O Envers grava a revisao apenas no commit. Tentativas desfeitas pelo retry da spec 10 nao geram revisao.
- `wallets_aud` recebe uma linha a cada alteracao de saldo e funciona como historico de saldo.
- Alteracoes em massa por `CriteriaUpdate`, `CriteriaDelete` ou query `UPDATE` direta nao sao capturadas pelo Envers e nao devem ser usadas em entidades auditadas.

## Fluxo Principal: Transferencia

1. `RequestIdFilter` define o `requestId`.
2. `TransferService` chama `CreateTransferUseCase`.
3. Em caso de sucesso, `CreateTransferUseCase` chama `AuditLogger.record` com `TRANSFER_CREATED` antes do commit.
4. No commit, o Envers grava as revisoes das carteiras e da transacao com `userId` e `requestId`.
5. Em caso de erro de negocio, `TransferService` chama `AuditLogger.recordFailure` com `TRANSFER_REJECTED` e relanca a excecao.

## Criterios de Aceite

- Toda resposta deve conter `X-Request-Id`.
- `X-Request-Id` valido enviado pelo cliente deve ser mantido.
- `X-Request-Id` invalido deve ser substituido por UUID.
- Cada acao da tabela deve gerar o evento correspondente.
- Transferencia recusada por saldo insuficiente deve gerar `TRANSFER_REJECTED` com `failure_code = INSUFFICIENT_BALANCE`, mesmo com rollback da operacao.
- Login com senha errada deve gerar `LOGIN_FAILED`.
- `UPDATE` e `DELETE` em `audit_events` devem falhar no banco.
- `metadata` nao deve conter senha, token ou documento completo.
- Transferencia concluida deve gerar revisoes em `wallets_aud` e `transactions_aud` com o mesmo `rev`.
- `revinfo` deve conter `user_id` e `request_id` da requisicao.
- `users_aud` nao deve conter `password_hash`.
- Retentativa da spec 10 nao deve gerar revisao duplicada.
- Teste de integracao deve validar eventos e revisoes de uma transferencia concluida e de uma recusada.
