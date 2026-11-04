# Fase 3 — Eventos e notificações

> **Status: MVP entregue em 2026-11-04** (design em `docs/superpowers/specs/2026-11-04-notificacoes-design.md`, plano em
> `docs/superpowers/plans/2026-11-04-notificacoes.md`). Entregue: outbox transacional, publicador agendado, RabbitMQ atrás de
> portas (`EventPublisher`/`EventHandler`, tópico `walletpay.events`, fila + DLQ por consumidor), evento `TransferCompleted`,
> notificações push (porta `PushGateway`, stub em log) e e-mail opcional (`EmailSender` SMTP compartilhado), preferências por
> usuário (`notification_preferences`), idempotência de entrega por (evento, usuário, canal). **Ainda pendente:** `MoneyReceived`/
> `BalanceUpdated` e depósito, histórico de notificações, DLQ consultável, métricas, migrar `Logging*Sender`, push real.
> O texto abaixo é a proposta original.

> **Proposta original.** Baseada em [`docs/core.md` §7–8](../../docs/core.md) e no código atual. Ainda não existem specs para esta fase.
> Itens marcados *(sugestão)* são recomendações minhas, não decisões do projeto.

## Objetivo

Transformar operações financeiras em **eventos confiáveis** e usá-los para notificar o usuário, criando a base sobre a
qual o motor de automações (Fase 4) vai operar.

## Fluxo conceitual (de `core.md` §7)

1. Operação financeira validada e persistida.
2. Evento registrado de forma confiável.
3. Consumidor processa o evento.
4. (Fase 4) Motor avalia regras ativas.
5. Resultado registrado e usuário notificado.

> Mensageria **não substitui** transação, idempotência nem validação de saldo.

## Pontos de integração no código atual

| Onde | O que muda |
|---|---|
| `CreateTransferUseCase` | Ao concluir, gravar evento `TransferCompleted` (+ `MoneyReceived` para o destinatário, `BalanceUpdated` para as duas carteiras) na **mesma transação**, ao lado de `AuditLogger.record` |
| `CreateDepositUseCase` | Gravar `MoneyReceived`/`BalanceUpdated` |
| `auth/infra/Logging*Sender` | Substituídos por adaptadores de e-mail reais atrás das interfaces `EmailVerificationSender` e `PasswordRecoverySender` (já existem) |
| `common/idempotency` | Reaproveitar a ideia para deduplicar o consumo de eventos |
| `audit` | Eventos de sistema com `AuditActorType.SYSTEM` |
| `docker-compose.yml` | Adicionar RabbitMQ |

## Componentes (sugestão)

- **Outbox:** tabela `outbox_events` (id, tipo, agregado, payload JSONB, criado em, publicado em, tentativas). Gravada no use case;
  um publicador agendado lê não publicados, publica no RabbitMQ e marca como publicado.
- **Broker:** RabbitMQ com exchange por tipo de evento, fila por consumidor e **DLQ** para mensagens que esgotarem retentativas.
- **Consumidores idempotentes:** tabela de eventos processados por (consumidor, id do evento).
- **Notificações:** entidade de notificação com histórico, canal e preferências; primeiro canal: e-mail.
- **Observabilidade:** `requestId` propagado como cabeçalho da mensagem; métricas de publicação, atraso e DLQ.

## Eventos iniciais

`MoneyReceived`, `TransferCompleted`, `BalanceUpdated` (de `core.md`). Fase 4 adiciona `AutomationTriggered/Executed/Failed`;
Fase 5 adiciona `BudgetLimitReached`; Fase 6 adiciona `PaymentConfirmed`.

## Critérios de aceite sugeridos

- Evento é persistido **se e somente se** a operação financeira for confirmada (rollback não deixa evento).
- Falha do broker não impede nem desfaz a operação; o evento é publicado depois (at-least-once).
- Reentrega do mesmo evento não gera notificação duplicada.
- Mensagem que falha repetidamente vai para a DLQ e fica visível/consultável.
- Notificação de transferência recebida/realizada chega ao usuário e fica no histórico.
- Testes de integração com broker real (Testcontainers) cobrindo publicação, reentrega e DLQ.

## Riscos

- **Dupla escrita** se a outbox for ignorada (publicar direto do use case perde consistência).
- **Ordem de eventos:** at-least-once não garante ordem; consumidores devem tolerar eventos fora de ordem.
- **Retentativas de saldo (spec 10):** cada tentativa abre nova transação; o evento só pode ser gravado na tentativa que confirma.
- **Aumento do tempo da transação interna** ao gravar mais linhas — monitorar.

## Perguntas em aberto

Ver decisões D2, D3 e D7 em [proximos-passos.md](proximos-passos.md#decisões-abertas-precisam-do-dono-do-projeto).

## Sugestão de decomposição em tasks

1. Infra: RabbitMQ no Compose + Testcontainers.
2. Outbox (tabela, gravação transacional, publicador).
3. Emissão de eventos em transferência e depósito.
4. Consumidor idempotente + DLQ + retentativas.
5. Adaptador de e-mail real e substituição dos `Logging*Sender`.
6. Notificações (modelo, histórico, preferências, endpoints).
7. Métricas e logs; testes ponta a ponta.
