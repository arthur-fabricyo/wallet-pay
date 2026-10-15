# Balance Concurrency Spec

## Objetivo

Garantir que o saldo das carteiras continue correto quando duas ou mais operacoes financeiras alteram a mesma carteira ao mesmo tempo.

## Contexto Atual

`CreateTransferUseCase` le as carteiras de origem e destino sem nenhum controle de concorrencia.
Duas transferencias simultaneas da mesma carteira podem ler o mesmo saldo, passar na validacao de saldo e a ultima gravacao sobrescreve a primeira.
O resultado e saldo incorreto ou negativo.

## Escopo

Esta spec cobre:

- Controle de concorrencia otimista na carteira.
- Retentativa automatica em caso de conflito.
- Resposta de erro quando as retentativas se esgotarem.
- Padrao obrigatorio para toda operacao que altera saldo.
- Infraestrutura de testes de integracao com banco real.
- Teste de concorrencia de transferencias.

Esta spec nao cobre:

- Lock pessimista.
- Idempotencia por `Idempotency-Key`.
- Limites de operacao.
- Filas ou processamento assincrono.
- Alteracao de payloads HTTP existentes.

## Decisao Tecnica

Estrategia escolhida: lock otimista com retentativa.

- `wallets` ganha a coluna `version`.
- `WalletEntity` ganha um campo anotado com `@Version`.
- Ao gravar uma carteira cuja versao mudou desde a leitura, o Hibernate lanca `OptimisticLockingFailureException`.
- A operacao inteira e executada novamente, em uma nova transacao, relendo saldo e versao.

Retentativa com `RetryTemplate` programatico do Spring Framework 7 (`org.springframework.core.retry`), sem a dependencia `spring-retry`.

Motivo para usar `RetryTemplate` em vez da anotacao `@Retryable`:

- Com `@Retryable`, a excecao final e lancada pelo proxy, fora do metodo anotado.
- O servico nao conseguiria converter a excecao final em `BalanceConflictException`.
- O servico tambem nao conseguiria registrar a falha na auditoria (spec 13) uma unica vez, apos a ultima tentativa.

## Estrutura em Duas Camadas

Toda operacao que altera saldo deve ser dividida em duas classes:

- Camada externa: executa a camada interna com `RetryTemplate`, sem `@Transactional`.
- Camada interna: anotada com `@Transactional`, contendo a regra de negocio atual.

As duas camadas devem ser beans diferentes.
Uma chamada interna da propria classe nao passa pelo proxy do Spring e nao aplicaria a retentativa.

O retry deve ficar fora da transacao.
Se ficasse dentro, a nova tentativa usaria o mesmo contexto de persistencia com os dados antigos e repetiria o conflito.

Para transferencias:

```text
TransactionController
  -> TransferService            (RetryTemplate, sem transacao)
       -> CreateTransferUseCase (@Transactional, regra atual)
```

`TransferService` fica em `transaction/service`.
`TransactionController` passa a depender de `TransferService` em vez de `CreateTransferUseCase`.

## Configuracao

Criar `config/ResilienceConfig.java` com o bean compartilhado por todas as operacoes de saldo:

```java
@Configuration
public class ResilienceConfig {

    @Bean
    public RetryTemplate balanceRetryTemplate() {
        RetryPolicy policy = RetryPolicy.builder()
                .includes(OptimisticLockingFailureException.class)
                .maxRetries(3)
                .delay(Duration.ofMillis(50))
                .jitter(Duration.ofMillis(25))
                .build();
        return new RetryTemplate(policy);
    }
}
```

Uso em `TransferService`:

```java
try {
    return balanceRetryTemplate.invoke(() -> createTransferUseCase.transfer(command));
} catch (OptimisticLockingFailureException exception) {
    throw new BalanceConflictException("Operacao concorrente na carteira, tente novamente");
}
```

- `invoke` relanca a excecao original quando as retentativas se esgotam.
- Excecoes fora de `includes` sao relancadas imediatamente, sem nova tentativa.

- `maxRetries = 3` significa ate 4 chamadas no total: 1 inicial e 3 retentativas.
- Apenas `OptimisticLockingFailureException` dispara retentativa.
- Erros de negocio, como saldo insuficiente, nunca disparam retentativa.

## Banco de Dados

### Migration V6__add_wallet_version.sql

```sql
ALTER TABLE wallets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

## Regras de Negocio

- Toda operacao que altera `wallets.balance` deve seguir a estrutura em duas camadas.
- Esta regra vale para transferencias, depositos (spec 11) e qualquer operacao futura de saldo.
- Conflito de versao deve ser resolvido com nova tentativa completa, nunca com merge manual de saldo.
- Apos esgotar as retentativas, a operacao deve falhar sem alterar nenhum saldo.
- O saldo nunca pode ficar negativo, mesmo sob concorrencia.
- A soma dos saldos de todas as carteiras deve ser preservada por transferencias.

## Erros

### 409 Conflict

Quando:

- As retentativas se esgotarem por conflito de versao da carteira.

Response:

```json
{
  "status": 409,
  "error": "Conflict",
  "code": "BALANCE_CONFLICT",
  "message": "Operacao concorrente na carteira, tente novamente",
  "fields": {}
}
```

Implementacao:

- `TransferService` captura `OptimisticLockingFailureException` relancada por `RetryTemplate.invoke` apos a ultima tentativa e lanca `BalanceConflictException`.
- `BalanceConflictException` fica em `wallet/service`.
- `GlobalExceptionHandler` mapeia `BalanceConflictException` para 409 com code `BALANCE_CONFLICT`.

## Testes de Integracao

Esta spec cria a infraestrutura de testes de integracao usada pelas specs 11 a 15.

Dependencias de teste:

- `org.springframework.boot:spring-boot-testcontainers`.
- `org.testcontainers:testcontainers-junit-jupiter`.
- `org.testcontainers:testcontainers-postgresql`.

Versoes gerenciadas pelo `spring-boot-starter-parent` 4.1.0 (Testcontainers 2.0.5).

Classe base `src/test/java/ElFabrica/Wallet_pay/support/AbstractIntegrationTest.java`:

- Sobe um container `postgres:16-alpine`, a mesma imagem do `docker-compose.yml`.
- Usa `@ServiceConnection` para configurar o datasource.
- Container compartilhado entre as classes de teste.
- Executa as migrations Flyway reais.
- Fornece helpers para criar usuario com e-mail verificado, carteira com saldo inicial e JWT valido.

Requisito: Docker disponivel para executar `./mvnw test`.

## Fluxo Principal

1. `TransactionController` recebe a requisicao e chama `TransferService`.
2. `TransferService` chama `CreateTransferUseCase` por meio de `balanceRetryTemplate.invoke`.
3. `CreateTransferUseCase` abre a transacao, le as carteiras com sua versao, valida, debita, credita e grava a transacao.
4. No commit, o Hibernate grava as carteiras com `WHERE version = ?`.
5. Se outra transacao alterou a carteira, o commit falha com `OptimisticLockingFailureException` e a transacao e desfeita.
6. `TransferService` executa novamente a partir do passo 3.
7. Apos 3 retentativas sem sucesso, `TransferService` lanca `BalanceConflictException`.

## Criterios de Aceite

- `wallets` deve ter a coluna `version`.
- `WalletEntity` deve ter campo com `@Version`.
- `TransferService` deve repetir a operacao ao receber `OptimisticLockingFailureException`.
- `TransferService` deve desistir apos 4 chamadas no total e retornar 409 `BALANCE_CONFLICT`.
- Erros de negocio nao devem disparar retentativa.
- O retry nao pode estar dentro de `@Transactional`.
- `balanceRetryTemplate` deve ser o unico mecanismo de retentativa de operacoes de saldo.
- Teste unitario deve verificar o numero de tentativas com o use case mockado.
- Teste de integracao: carteira com `100.00`, 20 threads transferindo `10.00` cada para outra carteira.
- Resultado esperado: exatamente 10 sucessos, saldo final da origem `0.00`, destino `+100.00`, nenhum saldo negativo.
- A soma de todos os saldos deve ser igual antes e depois do teste de concorrencia.
- Os testes existentes devem continuar passando.
- O contrato HTTP de `POST /transactions/transfers` nao deve mudar, exceto pelo novo 409.
