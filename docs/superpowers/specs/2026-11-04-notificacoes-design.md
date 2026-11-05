# Fase 3 — Notificações de transferência (design)

Status: rascunho para aprovação. Itens marcados **(decisão minha)** são escolhas do autor do design, não do dono do projeto.

## Intenção e porquê

Hoje toda operação é síncrona e o usuário não é avisado de nada. O dono do projeto quer que **quem envia e quem recebe uma
transferência seja notificado**, e quer uma base que permita **adicionar outros tipos e outros canais de notificação** sem mexer
no fluxo financeiro. Essa base de eventos também será o alicerce do motor de automações (Fase 4).

## Objetivos

1. Ao concluir uma transferência, **remetente e destinatário** recebem uma notificação **push**.
2. **E-mail** é um canal adicional que cada pessoa **habilita** (padrão: desligado **(decisão minha)**).
3. A notificação nunca atrasa, falha nem desfaz a transferência. Se broker ou canal cair, a transferência continua válida e a
   entrega é retentada depois.
4. Adicionar um **novo tipo** de notificação (ex.: depósito recebido) ou um **novo canal** (ex.: SMS) não exige alterar os
   use cases financeiros existentes nem os outros canais.
5. O broker (RabbitMQ) fica **atrás de uma abstração própria**, de modo que trocar por Kafka seja escrever um adaptador novo,
   sem mexer em use cases, outbox ou consumidores.
6. O envio de e-mail é uma **peça compartilhável** da aplicação, não exclusiva das notificações.
7. O e-mail pode ser testado de verdade com um servidor SMTP em container.

## Não objetivos (nesta entrega)

- Implementação real de push (FCM/APNs): o app Android é a Fase 7. Entra só o contrato e um stub.
- Migrar `LoggingEmailVerificationSender` e `LoggingPasswordRecoverySender` para o envio real de e-mail. A peça compartilhável
  fica pronta para isso, mas a migração é outra entrega.
- Notificações para depósito, limites, automações etc. Só transferência (enviada/recebida).
- Tela/endpoint de histórico de notificações, DLQ consultável por API, métricas e dashboards.
- Motor de automações, categorias, cofres, Pix.

## Anti-objetivos (seria falha mesmo que "funcione")

- Publicar no broker dentro da transação financeira, ou depender do broker para confirmar a transferência.
- Código de domínio ou de use case importando classes do RabbitMQ/Spring AMQP.
- Notificação duplicada para a mesma transferência quando o evento é reentregue.
- Vazar dados do outro usuário além do necessário (ver Conteúdo).

## Restrições e contexto do código

- Java 21, Spring Boot 4.1, PostgreSQL 16, Flyway (migrations atuais V1–V10), pacote raiz `ElFabrica.Wallet_pay`.
- `CreateTransferUseCase.executeTransfer` roda dentro da transação e já grava auditoria ao lado da transação; o evento entra no
  mesmo ponto. Há retentativa por lock otimista: o evento só pode ser gravado na tentativa que confirma (a gravação na mesma
  transação garante isso). O replay idempotente não reexecuta `executeTransfer`, então não duplica evento.
- Já existe `Testcontainers` (PostgreSQL) nos testes; `docker-compose.yml` hoje só tem Postgres.
- Verificação de e-mail e recuperação de senha têm portas próprias (`EmailVerificationSender`, `PasswordRecoverySender`) com
  implementação que só escreve em log.

## Visão geral da solução

```
CreateTransferUseCase ──(mesma transação)──► outbox_events
                                                │
                              publicador agendado (lê não publicados)
                                                ▼
                                   EventPublisher (porta)
                                                ▼
                              adaptador RabbitMQ  ──►  fila do consumidor
                                                          │
                                        EventConsumer (porta/handler)
                                                          ▼
                                  NotificationService (idempotente por evento)
                                      ├─ resolve destinatários e preferências
                                      └─ para cada canal habilitado:
                                           NotificationChannel (porta)
                                             ├─ PushChannel (porta p/ app; stub em log)
                                             └─ EmailChannel ──► EmailSender (compartilhado) ──► SMTP
```

## Partes

### 1. Eventos e outbox

- Evento de domínio neutro, serializado como JSON: `eventId` (UUID), `type`, `occurredAt`, `aggregateId`, `requestId`, `payload`.
  Formato independente de RabbitMQ/Kafka.
- Tabela `outbox_events` (nova migration): id, tipo, id do agregado, payload JSONB, criado em, publicado em, tentativas, último erro.
- `TransferCompleted` gravado na mesma transação da transferência. Payload mínimo: id da transação, valor, moeda, id do
  usuário remetente, id do usuário destinatário, data. **(decisão minha)** Um único evento `TransferCompleted`; remetente e
  destinatário viram duas notificações na camada de notificação, não dois eventos.
- Publicador agendado: lê não publicados em ordem de criação, publica, marca como publicado; falha incrementa tentativas e
  tenta de novo depois. Entrega **at-least-once**; consumidor tolera duplicata e desordem.

### 2. Abstração de mensageria (desacoplada)

- Porta `EventPublisher` (publicar um evento) e um contrato de consumo (registrar um handler por tipo de evento), definidos
  em pacote da aplicação sem dependência de AMQP.
- Adaptador RabbitMQ implementa as duas. Trocar por Kafka = novo adaptador + configuração, sem alterar outbox, use cases ou
  `NotificationService`.
- Cabeçalho `requestId` propagado na mensagem (correlação com `X-Request-Id`).
- Mensagem que esgota as retentativas de consumo vai para fila de mensagens mortas (DLQ) do RabbitMQ. Consultar a DLQ por
  API está fora de escopo.

### 3. Notificações

- Tipo de notificação (nesta entrega: `TRANSFER_SENT`, `TRANSFER_RECEIVED`) separado de canal. Um tipo novo é um mapeamento
  evento → notificação(ões); um canal novo é uma implementação da porta `NotificationChannel`.
- `NotificationService` consome o evento, decide quem é notificado (remetente e destinatário), consulta as preferências de cada
  um e chama os canais habilitados.
- **Idempotência do consumo:** tabela de eventos processados por (consumidor, `eventId`); reentrega não reenvia.
  Falha em um canal não impede nem repete os outros canais já entregues **(decisão minha: controle por (evento, usuário, canal))**.
- **Preferências:** tabela própria `notification_preferences` (nova migration), **vinculada ao usuário** (`user_id`, FK para
  `users`), não à carteira. Motivo **(decisão minha)**: a preferência é da pessoa (e-mail e push são endereços e dispositivos
  dela), não da conta financeira; hoje há uma carteira por usuário, mas se um dia houver várias, a preferência não deve se
  multiplicar. Estrutura: uma linha por (`user_id`, canal), com `enabled`, criada/atualizada em, e unicidade em (`user_id`, canal).
  Sem linha = padrão do canal: push ligado, e-mail desligado. Assim, novos canais não exigem migration de coluna nem
  backfill. O usuário lê e altera as próprias preferências por endpoint autenticado (ver Pergunta em aberto 1).
- **Push:** porta `PushChannel` (contrato que o app implementará/consumirá) com implementação padrão em stub que registra em log.
  Quando o app existir, a entrega real de push entra sem mudar o `NotificationService`. Cadastro de dispositivos/tokens é da Fase 7.

### 4. E-mail compartilhável

- Porta `EmailSender` (destinatário, assunto, corpo) em pacote comum, sem relação com notificações, e adaptador SMTP
  (Spring Mail). Configurável por propriedades (host, porta, remetente padrão).
- `EmailChannel` é só mais um `NotificationChannel` que usa o `EmailSender`.
- Verificação de cadastro e recuperação de senha poderão usar o mesmo `EmailSender` depois; não migram agora.
- Endereço do usuário vem do cadastro (e-mail já verificado, pois o login exige verificação).

### 5. Conteúdo das notificações

- Remetente vê: valor, nome ou identificador do destinatário, data. Destinatário vê: valor, do remetente, data.
  **(decisão minha)** Usar nome de exibição do outro usuário, sem CPF/CNPJ, e-mail ou saldo.
- Textos em português. Formatação do valor como BRL.

### 6. Infraestrutura e testes

- `docker-compose.yml` ganha RabbitMQ (com console de gestão) e um servidor SMTP de desenvolvimento **(decisão minha: Mailpit)**,
  com caixa de entrada web para ver os e-mails.
- Testes de integração com Testcontainers: PostgreSQL (já existe), RabbitMQ e o servidor SMTP.
- Cobrir: evento gravado só se a transferência confirmar (rollback não deixa evento); transferência continua válida com broker
  fora do ar e o evento sai depois; reentrega não duplica notificação; remetente e destinatário notificados; e-mail só sai para
  quem habilitou; e-mail aparece na caixa do SMTP de teste; mensagem que falha repetidamente cai na DLQ.

## O que fica para o construtor

- Nomes de pacotes e classes, organização interna dos módulos (`event`, `notification`, `common/email` são sugestões).
- Estratégia exata de retentativa/backoff e intervalo do publicador agendado.
- Biblioteca de serialização, estrutura exata das tabelas além do descrito, nomes de exchange/fila.
- Se o publicador agendado usa lock para rodar com mais de uma instância.
- Detalhes do endpoint de preferência, respeitando o contrato de erros e a convenção de controllers do projeto.

## Perguntas em aberto

1. ~~Como o usuário habilita o e-mail~~ Resolvido: tabela `notification_preferences` ligada ao usuário (ver Parte 3), com
   endpoint autenticado para ler e alterar as preferências do próprio usuário.
2. ~~Nome de exibição do outro usuário~~ Resolvido: a tabela `users` já tem a coluna `name` (migration V1); usar esse campo.
