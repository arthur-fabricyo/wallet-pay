# Registro de decisões técnicas

Decisões já tomadas e o porquê. Origem: specs 10–15, a seção "Decisões de Implementação" do
[plano](../../docs/superpowers/plans/2026-10-15-fases-1-2.md) e o design/plano da Fase 3
(`docs/superpowers/specs/2026-11-04-notificacoes-design.md`). Formato: decisão · motivo · consequência.

## Concorrência e saldo

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T01 | **Lock otimista** (`@Version` em `wallets`) com retentativa, não lock pessimista | Evita bloqueio de linha e permite conflito explícito | Conflito persistente vira `409 BALANCE_CONFLICT` |
| T02 | `RetryTemplate` programático (Spring 7), sem `spring-retry` nem `@Retryable` | Com `@Retryable` a exceção final sai do proxy; o serviço não conseguiria converter nem auditar uma única vez | Padrão de duas camadas obrigatório |
| T03 | Retry **fora** da transação; camadas em beans distintos | Nova tentativa precisa de novo contexto de persistência; chamada interna não passa pelo proxy | Toda operação de saldo segue o [padrão](../03-arquitetura/operacoes-de-saldo.md) |
| T04 | `hibernate.order_updates=true` | Transferências cruzadas faziam deadlock | UPDATEs ordenados por PK (commit `64647ae`) |
| T05 | Testes de concorrência simulam o cliente que repete em `409` | "Exatamente 10 sucessos" só é determinístico assim (20 threads, 4 tentativas por chamada) | — |

## Idempotência

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T06 | Chaves no **PostgreSQL**, na mesma transação da operação, não Redis | Com Redis, crash entre commit e gravação duplicaria cobrança; rollback deixaria chave órfã | Sem estado "em processamento"; `INSERT … ON CONFLICT DO NOTHING` bloqueia concorrentes |
| T07 | Hash do **comando normalizado**, não do JSON bruto | `"10.0"` = `"10.00"` | `Money` usado antes do hash |
| T08 | Só respostas de sucesso são salvas | Erro desfaz a transação e libera a chave | Cliente pode repetir com a mesma chave |
| T09 | `Idempotency-Key` lido com `required=false` e validado em `IdempotencyKeys.require` | Vazio/espaços também devem ser `400 IDEMPOTENCY_KEY_REQUIRED` | Nunca grava chave `""` |

## Auditoria

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T10 | Dois mecanismos: `audit_events` + Envers | Eventos capturam tentativas que falharam; Envers captura estado confirmado | `wallets_aud` = histórico de saldo |
| T11 | Falha auditada em transação própria (`REQUIRES_NEW`); sucesso na transação da operação (`MANDATORY`) | Rollback apagaria o registro da falha | Login falho também foi movido para fora da transação (`9063138`) |
| T12 | `audit_events` append-only por trigger | Imutabilidade | UPDATE/DELETE falham no banco |
| T13 | `do_not_audit_optimistic_locking_field=false` e prefixo `org.hibernate.envers.*` | Senão `wallets_aud.version` não existe / propriedade é ignorada | Documentado em `705af23` |
| T14 | `JdbcClient` (não JPA) para `idempotency_keys` e `audit_events` | SQL específico do PostgreSQL: `ON CONFLICT`, `jsonb`, append-only | Participa da mesma transação do `JpaTransactionManager` |
| T15 | Envers é consciente da descontinuação no Hibernate 8 | O novo `@Audited` do core lê o formato do Envers | Migração futura planejada |

## Erros e contratos

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T16 | `BusinessException` com `ErrorCode`; um handler | Mesmo code na resposta e em `audit_events.failure_code` | [Catálogo](../03-arquitetura/erros.md) único |
| T17 | Códigos de token mantêm o contrato das specs 04/05 | Nomes alternativos da spec 15 quebrariam clientes e apagariam a diferença entre expirado e já usado | Spec 15 corrigida na Task 2 |
| T18 | `OptimisticLockingFailureException` fora de saldo → `409 DATA_CONFLICT` | Ex.: dois `PUT /wallets/me/limits` simultâneos | Não vira 500 |
| T19 | Sem RFC 9457 (`ProblemDetail`) por enquanto | Fora de escopo da spec 15 | Possível evolução |

## Dinheiro, tempo e limites

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T20 | Classe `Money` única; rejeita mais de 17 dígitos inteiros | Nunca estourar `NUMERIC(19,2)` | `400 INVALID_AMOUNT` |
| T21 | Valores na API como string com 2 casas | Evitar erro de ponto flutuante | `Money.format` |
| T22 | **Relógio único** (`Clock`); factories de transação recebem `Instant` | `created_at` e janelas de limite não podem usar relógios diferentes | Testes usam `MutableClock` |
| T23 | Limites: efetivo = `min(global, usuário)`; usuário só reduz | Segurança | `null` volta ao global |
| T24 | Períodos em `America/Sao_Paulo`, janela noturna 20:00–06:00 | Regra de negócio brasileira, servidor pode estar em UTC | Ex.: 30/09 23:00 BRT não conta em outubro |
| T25 | `LimitsProperties`: horários e fuso como `String` validadas no construtor | Não depender de conversores do binder | Validação na subida |
| T26 | Sem lock extra para limites | Toda operação altera a versão da carteira; a retentativa recalcula | Duas transferências simultâneas que estouram o diário → uma passa, uma falha |

## Estrutura e processo

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T27 | Monólito modular | `core.md` §6 | Separar serviços só com necessidade real |
| T28 | Pasta `controllers` só com controllers; DTOs em `dto/` com sufixo `DTO` | Spec 09, padrão de mercado | — |
| T29 | Testes sem Mockito (fakes por subclasse ou `Proxy`) | Padrão herdado dos testes existentes | ⚠️ Contrariada na Fase 3 (`@MockitoSpyBean`); revisar (pendência #37) |
| T30 | Infra de testes e catálogo de erros antes das features | Features já nascem com `ErrorCode` e testes de integração | Ordem das tasks do plano |
| T31 | Um commit por task, conventional commits em inglês, textos de domínio em português sem acento | Rastreabilidade | ⚠️ Textos das notificações da Fase 3 têm acento; revisar (pendência #37) |
| T32 | Integrações reais (banco/Pix/investimentos) ficam simuladas no MVP | Escopo regulatório | Fase 8 só com sandbox |

## Eventos e notificações (Fase 3)

| ID | Decisão | Motivo | Consequência |
|---|---|---|---|
| T33 | **Transactional Outbox**: o use case só grava em `outbox_events` (`MANDATORY`); um job publica | Operação financeira não pode depender do broker nem perder evento em rollback | Entrega *at-least-once*; handlers precisam ser idempotentes |
| T34 | RabbitMQ atrás das portas `EventPublisher`/`EventHandler`; AMQP só em `common/event/rabbit` | Trocar de broker (ex.: Kafka) = novo adaptador (D2) | `ArchitectureBoundaryTest` falha se AMQP vazar |
| T35 | Um tópico `walletpay.events`, routing key = tipo do evento; uma fila + DLQ por (consumidor, tipo) | Cada consumidor recebe sua cópia e falha isolado | Novo `EventHandler` ganha fila sozinho; tipo de evento sem pontos |
| T36 | Publicador espera a confirmação do broker antes de marcar `published_at` | Sem confirmação, evento podia ser marcado publicado e se perder (`8b0cc1f`) | Nack ou timeout mantêm a linha pendente |
| T37 | Idempotência de entrega por (evento, usuário, canal), não tabela genérica de eventos processados | Falha de um canal não pode impedir os outros nem duplicar o que já foi entregue (`4b4ce9e`) | Duas notificações do mesmo evento para o mesmo usuário colidem: cada uma precisa de evento próprio |
| T38 | Broker e e-mail fora do `/actuator/health`; conexão ao broker com timeout de 5 s | Indisponibilidade do broker não pode derrubar a aplicação (`8a0bf89`) | Saúde do broker precisa de métrica/alerta próprio (esteira A08) |
| T39 | Testes com containers compartilhados na JVM e namespace aleatório do broker por contexto (D7) | Tempo da suíte e isolamento entre contextos Spring | Filas de teste com prefixo `<uuid>.` |

## Como registrar uma nova decisão

Acrescente uma linha com ID sequencial (`T40`, …), motivo e consequência. Se a decisão alterar uma spec, atualize a spec também.
