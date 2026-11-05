# Notificações de transferência (Fase 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remetente e destinatário de uma transferência recebem notificação push; o e-mail é um canal opcional por usuário; tudo via outbox + RabbitMQ atrás de portas que permitem trocar por Kafka.

**Architecture:** `CreateTransferUseCase` grava um evento `TransferCompleted` na tabela `outbox_events` na mesma transação. Um job agendado publica o outbox por uma porta `EventPublisher` (adaptador RabbitMQ). Um consumidor (`EventHandler`) idempotente transforma o evento em notificações e as entrega pelos canais habilitados (`NotificationChannel`: push via `PushGateway`, e-mail via `EmailSender` compartilhado).

**Tech Stack:** Java 21, Spring Boot 4.1 (Jackson 3, pacote `tools.jackson`), Spring AMQP/RabbitMQ, Spring Mail, PostgreSQL 16 + Flyway, Mailpit (SMTP de desenvolvimento), Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-04-notificacoes-design.md`

## Global Constraints

- Trabalhar na branch `feat/fase-3-notificacoes`, criada a partir de `dev`. Um commit por task, conventional commits em inglês, terminando com a linha `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Pacote raiz `ElFabrica.Wallet_pay`; caminho de fonte `src/main/java/ElFabrica/Wallet_pay` (abreviado `B/` abaixo); testes em `src/test/java/ElFabrica/Wallet_pay`.
- Nenhuma classe fora de `common/event/rabbit` pode importar `org.springframework.amqp` ou `com.rabbitmq`. Um teste de arquitetura (Task 4) garante isso.
- A transferência nunca pode depender do broker: publicar só pelo job do outbox, nunca dentro do use case.
- Migrations novas continuam a numeração Flyway: V11 (outbox), V12 (preferências), V13 (entregas). `ddl-auto=validate` permanece.
- Push sempre ligado; e-mail desligado por padrão; sem linha em `notification_preferences` = padrão do canal.
- Conteúdo das notificações: nome (`users.name`) do outro usuário, valor em BRL, data. Nunca CPF/CNPJ, e-mail ou saldo do outro usuário. Assunto de e-mail fixo (sem dados do usuário).
- Textos em português. Erros usam o catálogo `ErrorCode`/`BusinessException` existente.
- Testes de integração estendem `AbstractIntegrationTest` (que trunca todas as tabelas públicas a cada teste). Verificação final de cada task: `./mvnw test` verde (Docker ligado; `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.3/jbr"`, ver `kb/07-guias/como-rodar-e-testar.md`).

## Review Focus

1. Transferência que falha (saldo insuficiente, limite excedido) não deixa linha no outbox. → Task 5.
2. Broker fora do ar: a transferência é confirmada; a linha do outbox fica não publicada com `attempts` incrementado e sai no ciclo seguinte. → Task 3.
3. Evento reentregue pelo broker não duplica notificação nem reenvia canal já entregue; falha de um canal não repete os já entregues. → Task 7.
4. Evento que referencia usuário inexistente não pode travar a fila em loop: registra log e é descartado. → Task 7.
5. Nome de usuário com quebra de linha/HTML não pode injetar cabeçalho nem quebrar o e-mail. → Task 8.
6. Preferências: sem autenticação → 401; canal desconhecido → 400; tentar desligar PUSH → 400. → Task 6.

---

### Task 1: Infraestrutura (dependências, compose, containers de teste)

**Files:**
- Modify: `pom.xml` (adicionar `spring-boot-starter-amqp`, `spring-boot-starter-mail`; test: `org.testcontainers:testcontainers-rabbitmq` e, se existir no Boot 4.1, `spring-boot-starter-amqp-test`)
- Modify: `docker-compose.yml` (serviços `rabbitmq:3-management-alpine` nas portas 5672/15672 e `axllent/mailpit` nas portas 1025 SMTP / 8025 web, ambos com healthcheck)
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/ElFabrica/Wallet_pay/support/IntegrationTestConfiguration.java`
- Modify: `src/test/java/ElFabrica/Wallet_pay/support/AbstractIntegrationTest.java` (propriedade `walletpay.events.outbox.poll-delay=PT1H` no `@SpringBootTest`, para o job não rodar sozinho nos testes)
- Create: `src/test/java/ElFabrica/Wallet_pay/support/MailpitClient.java`
- Test: `src/test/java/ElFabrica/Wallet_pay/support/InfrastructureSmokeTest.java`

**Interfaces:**
- Produces: propriedades `spring.rabbitmq.*` (padrão localhost:5672, guest/guest), `spring.mail.host/port` (padrão localhost:1025), `walletpay.email.from` (padrão `no-reply@walletpay.local`), `walletpay.events.outbox.poll-delay` (padrão `PT2S`).
- Produces (testes): bean `RabbitMQContainer` com `@ServiceConnection`; bean `GenericContainer<?>` Mailpit registrando `spring.mail.host/port` via `DynamicPropertyRegistrar`; `MailpitClient` (`@Component` de teste ou classe instanciável a partir do container) com `List<MailpitMessage> messages()`, `void clear()`, onde `MailpitMessage(String to, String subject, String body)`, usando a API HTTP `GET/DELETE /api/v1/messages` do Mailpit.

- [x] **Step 1: Write the failing test** `InfrastructureSmokeTest extends AbstractIntegrationTest`: `rabbitConnectionIsAvailable()` (`RabbitTemplate.execute(channel -> channel.isOpen())` é `true`) e `mailpitStartsEmpty()` (`mailpitClient.clear()` seguido de `messages()` é vazio).
- [x] **Step 2: Run** `./mvnw test -Dtest=InfrastructureSmokeTest` — Expected: FAIL (dependências/beans ausentes).
- [x] **Step 3: Implement** as dependências, o compose, as propriedades e os beans de container acima. Documentar RabbitMQ (`http://localhost:15672`, guest/guest) e Mailpit (`http://localhost:8025`) em `kb/07-guias/como-rodar-e-testar.md`.
- [x] **Step 4: Run** `./mvnw test` — Expected: suíte inteira verde (nenhuma regressão ao subir os novos containers).
- [x] **Step 5: Commit** `chore: add rabbitmq and mailpit infrastructure`

---

### Task 2: EmailSender compartilhado

**Files:**
- Create: `B/common/email/EmailSender.java`, `B/common/email/EmailMessage.java`, `B/common/email/SmtpEmailSender.java`, `B/common/email/EmailSendException.java`
- Test: `src/test/java/ElFabrica/Wallet_pay/common/email/SmtpEmailSenderIntegrationTest.java`

**Interfaces:**
- Produces: `interface EmailSender { void send(EmailMessage message); }`; `record EmailMessage(String to, String subject, String body)`; `SmtpEmailSender` (`@Component`, `JavaMailSender`, remetente de `walletpay.email.from`) lança `EmailSendException` (runtime) em falha SMTP.

- [x] **Step 1: Write the failing tests:** (a) `sendsMessageThroughSmtp`: após `send(new EmailMessage("ana@email.com", "Assunto", "Corpo"))`, `mailpitClient.messages()` tem 1 mensagem com `to="ana@email.com"`, `subject="Assunto"`, corpo contendo `Corpo`; (b) `rejectsSubjectWithLineBreaks`: assunto `"a\r\nBcc: x@y.com"` lança `IllegalArgumentException` e nada chega ao Mailpit (validação em `EmailMessage`).
- [x] **Step 2: Run** `./mvnw test -Dtest=SmtpEmailSenderIntegrationTest` — Expected: FAIL.
- [x] **Step 3: Implement** os tipos acima; `EmailMessage` valida no construtor (to com formato simples, subject sem `\r`/`\n`); `SmtpEmailSender` usa `SimpleMailMessage`/`MimeMessageHelper` com UTF-8 e traduz `MailException` para `EmailSendException`.
- [x] **Step 4: Run** o teste — Expected: PASS.
- [x] **Step 5: Commit** `feat: add shared smtp email sender`

---

### Task 3: Contrato de evento e outbox

**Files:**
- Create: `B/common/event/DomainEvent.java`, `B/common/event/EventPublisher.java`, `B/common/event/EventHandler.java`, `B/common/event/OutboxRecorder.java`, `B/common/event/OutboxPublisherJob.java`
- Create: `src/main/resources/db/migration/V11__create_outbox_events.sql`
- Test: `src/test/java/ElFabrica/Wallet_pay/common/event/OutboxIntegrationTest.java`

**Interfaces:**
- Produces:
  - `record DomainEvent(UUID eventId, String type, Instant occurredAt, UUID aggregateId, String requestId, tools.jackson.databind.JsonNode payload)` — formato neutro de broker.
  - `interface EventPublisher { void publish(DomainEvent event); }` (lança `RuntimeException` em falha).
  - `interface EventHandler { String consumerName(); String eventType(); void handle(DomainEvent event); }` (exceção = reentrega/retentativa).
  - `OutboxRecorder.record(DomainEvent event)` com `@Transactional(propagation = MANDATORY)`; preenche `requestId` do MDC (`RequestIdFilter.MDC_KEY`) quando o evento vier sem.
  - `OutboxPublisherJob.publishPending(): int` (retorna quantidade publicada), agendado com `@Scheduled(fixedDelayString = "${walletpay.events.outbox.poll-delay:PT2S}")`; lê não publicados em ordem de `created_at`, em lote, com `for update skip locked`; sucesso marca `published_at`; falha incrementa `attempts`, grava `last_error` e segue para o próximo ciclo.
- Tabela `outbox_events`: `id UUID PK`, `event_type VARCHAR`, `aggregate_id UUID`, `payload JSONB`, `request_id VARCHAR NULL`, `created_at`, `published_at NULL`, `attempts INT DEFAULT 0`, `last_error TEXT NULL`; índice parcial nos não publicados.

- [x] **Step 1: Write the failing tests** (`EventPublisher` substituído por um bean de teste controlável `ControllableEventPublisher` com `failNext` e lista `published`): (a) `recordsEventInsideTransaction`: `record` dentro de `transactionTemplate.execute` cria 1 linha com `published_at` nulo; (b) `recordFailsOutsideTransaction`: chamar `record` sem transação lança `IllegalTransactionStateException`; (c) `publishPendingPublishesAndMarksRows`: após `publishPending()`, `published` tem o evento e a linha tem `published_at`; (d) `brokerFailureKeepsRowAndRetriesLater`: com `failNext`, `publishPending()` retorna 0, linha segue com `published_at` nulo, `attempts=1` e `last_error` preenchido; novo `publishPending()` publica e marca; (e) `publishesInCreationOrder`.
- [x] **Step 2: Run** `./mvnw test -Dtest=OutboxIntegrationTest` — Expected: FAIL.
- [x] **Step 3: Implement** a migration e as classes acima usando `JdbcClient` (mesmo estilo de `AuditLogger`), `Clock` injetado e `JsonMapper` (Jackson 3). Provisório: até a Task 4, `EventPublisher` não tem bean de produção; o bean de teste cobre o contexto (ou registrar um `@ConditionalOnMissingBean` no-op que a Task 4 substitui, a critério do construtor).
- [x] **Step 4: Run** `./mvnw test` — Expected: verde.
- [x] **Step 5: Commit** `feat: add transactional outbox and event contracts`

---

### Task 4: Adaptador RabbitMQ (publicar, consumir, DLQ)

**Files:**
- Create: `B/common/event/rabbit/RabbitEventPublisher.java`, `B/common/event/rabbit/RabbitEventConsumerConfig.java`, `B/common/event/rabbit/RabbitTopology.java`
- Modify: `src/main/resources/application.properties` (retry do listener: 3 tentativas com backoff; `default-requeue-rejected=false`)
- Test: `src/test/java/ElFabrica/Wallet_pay/common/event/rabbit/RabbitEventAdapterIntegrationTest.java`, `src/test/java/ElFabrica/Wallet_pay/ArchitectureBoundaryTest.java`

**Interfaces:**
- Consumes: `EventPublisher`, `EventHandler` (Task 3).
- Produces: `RabbitEventPublisher implements EventPublisher` (exchange `walletpay.events` do tipo topic, routing key = `event.type()`, mensagem JSON persistente, cabeçalhos `eventId` e `requestId`); topologia declarada para **cada bean `EventHandler`**: fila `walletpay.<consumerName>.<eventType>` ligada à exchange, com DLX/DLQ `walletpay.<consumerName>.<eventType>.dlq`; um listener por handler que desserializa em `DomainEvent`, restaura o `requestId` no MDC durante `handle` e deixa a exceção propagar para retentativa/DLQ.

- [x] **Step 1: Write the failing tests** (handler de teste `RecordingHandler` registrado como bean; `@BeforeEach` purga as filas com `RabbitAdmin.purgeQueue`): (a) `publishedEventReachesMatchingHandler` (usa `await` com timeout curto); (b) `handlerForOtherTypeDoesNotReceiveIt`; (c) `failingHandlerMessageEndsUpInDeadLetterQueue`: handler que sempre lança → após as retentativas a mensagem aparece na `.dlq`; (d) `requestIdIsAvailableInMdcWhileHandling`. `ArchitectureBoundaryTest`: varre o código-fonte (`src/main/java`) e falha se algum arquivo fora de `common/event/rabbit` contiver `org.springframework.amqp` ou `com.rabbitmq`.
- [x] **Step 2: Run** `./mvnw test -Dtest='RabbitEventAdapterIntegrationTest,ArchitectureBoundaryTest'` — Expected: FAIL.
- [x] **Step 3: Implement** o adaptador e a topologia; serialização com o `JsonMapper` do projeto (não o conversor padrão do AMQP com tipos de classe), para o formato ficar neutro. Remover o publisher provisório da Task 3 se houver.
- [x] **Step 4: Run** `./mvnw test` — Expected: verde.
- [x] **Step 5: Commit** `feat: add rabbitmq adapter for event publishing and consumption`

---

### Task 5: Emitir `TransferCompleted` na transferência

**Files:**
- Create: `B/transaction/event/TransferCompletedEvent.java`
- Modify: `B/transaction/service/CreateTransferUseCase.java` (injetar `OutboxRecorder`; gravar o evento em `executeTransfer`, ao lado de `auditLogger.record`)
- Test: `src/test/java/ElFabrica/Wallet_pay/transaction/TransferOutboxIntegrationTest.java`

**Interfaces:**
- Consumes: `OutboxRecorder.record(DomainEvent)` (Task 3).
- Produces: `TransferCompletedEvent.TYPE = "TransferCompleted"`; `record TransferCompletedPayload(UUID transactionId, String amount, String currency, UUID senderUserId, UUID receiverUserId)` (`amount` via `Money.format`); `static DomainEvent TransferCompletedEvent.of(TransactionEntity tx, UUID senderUserId, UUID receiverUserId, ObjectMapper/JsonMapper mapper, Instant occurredAt)` com `aggregateId = transactionId`.

- [x] **Step 1: Write the failing tests** (via `POST /transactions/transfers` com `Idempotency-Key`, usando `createVerifiedUser`): (a) `successfulTransferWritesOneOutboxRow` — 1 linha `TransferCompleted`, payload com ids dos dois usuários e `amount="100.00"`; (b) `insufficientBalanceWritesNoOutboxRow`; (c) `limitExceededWritesNoOutboxRow`; (d) `idempotentReplayDoesNotDuplicateEvent` — mesma `Idempotency-Key` duas vezes → 1 linha; (e) `transferStillSucceedsWhenNothingIsPublished` — `OutboxPublisherJob` nunca executado, saldo debitado/creditado e comprovante disponível.
- [x] **Step 2: Run** `./mvnw test -Dtest=TransferOutboxIntegrationTest` — Expected: FAIL.
- [x] **Step 3: Implement** o evento e a chamada; atualizar o teste unitário existente de `CreateTransferUseCase` (se houver construção manual) para o novo parâmetro do construtor.
- [x] **Step 4: Run** `./mvnw test` — Expected: verde (incluindo `TransferIntegrationTest` existente e concorrência).
- [x] **Step 5: Commit** `feat: record TransferCompleted event in the transfer transaction`

---

### Task 6: Preferências de notificação

**Files:**
- Create: `src/main/resources/db/migration/V12__create_notification_preferences.sql`
- Create: `B/notification/NotificationChannelType.java`, `B/notification/domain/NotificationPreferenceEntity.java`, `B/notification/repository/NotificationPreferenceRepository.java`, `B/notification/service/NotificationPreferenceService.java`, `B/notification/controllers/NotificationPreferenceController.java`, `B/notification/dto/NotificationPreferenceResponseDTO.java`, `B/notification/dto/UpdateNotificationPreferenceRequestDTO.java`
- Test: `src/test/java/ElFabrica/Wallet_pay/notification/NotificationPreferenceIntegrationTest.java`

**Interfaces:**
- Produces: `enum NotificationChannelType { PUSH, EMAIL }` com `boolean defaultEnabled()` (PUSH `true`, EMAIL `false`) e `boolean userControllable()` (PUSH `false`, EMAIL `true`); `NotificationPreferenceService.isEnabled(UUID userId, NotificationChannelType channel): boolean` (sem linha → padrão); `NotificationPreferenceService.list(UUID userId)` e `update(UUID userId, NotificationChannelType channel, boolean enabled)`.
- Tabela `notification_preferences`: `id UUID PK`, `user_id UUID NOT NULL` FK `users(id)` `ON DELETE CASCADE`, `channel VARCHAR(20)`, `enabled BOOLEAN`, `created_at`, `updated_at`, `UNIQUE (user_id, channel)`.
- Endpoints (JWT, `jwt.getSubject()` como em `WalletController`): `GET /notifications/preferences` → `[{channel, enabled}]` para todos os canais com valor efetivo; `PUT /notifications/preferences/{channel}` com `{ "enabled": boolean }` → 200 com o item atualizado. **Decisão do plano:** `PUSH` não é alterável (400 `INVALID_PARAMETER`), canal desconhecido → 400 `INVALID_PARAMETER`.

- [x] **Step 1: Write the failing tests:** `defaultsAreEmailOffPushOn`; `enablingEmailPersistsAndIsReflectedOnGet`; `updatingTwiceKeepsSingleRow`; `disablingPushIsRejectedWith400`; `unknownChannelIsRejectedWith400`; `requiresAuthentication` (sem token → 401); `usersCannotSeeEachOthersPreferences` (A habilita, B continua desligado).
- [x] **Step 2: Run** `./mvnw test -Dtest=NotificationPreferenceIntegrationTest` — Expected: FAIL.
- [x] **Step 3: Implement** seguindo as convenções de controller/DTO existentes (UserController/WalletController); upsert pela chave (user_id, channel); documentar as rotas em `kb/03-arquitetura/api.md`.
- [x] **Step 4: Run** `./mvnw test` — Expected: verde.
- [x] **Step 5: Commit** `feat: add per-user notification preferences`

---

### Task 7: Núcleo de notificações (canais, push stub, idempotência)

**Files:**
- Create: `src/main/resources/db/migration/V13__create_notification_deliveries.sql`
- Create: `B/notification/Notification.java`, `B/notification/channel/NotificationChannel.java`, `B/notification/channel/PushChannel.java`, `B/notification/push/PushGateway.java`, `B/notification/push/PushMessage.java`, `B/notification/push/LoggingPushGateway.java`, `B/notification/service/NotificationDispatcher.java`, `B/notification/service/TransferCompletedHandler.java`
- Test: `src/test/java/ElFabrica/Wallet_pay/notification/TransferNotificationIntegrationTest.java`

**Interfaces:**
- Consumes: `EventHandler`/`DomainEvent` (Task 3), `TransferCompletedEvent` (Task 5), `NotificationPreferenceService.isEnabled` (Task 6).
- Produces:
  - `record Notification(UUID eventId, UUID userId, String kind, String title, String body)` (`kind` ∈ `TRANSFER_SENT`, `TRANSFER_RECEIVED`).
  - `interface NotificationChannel { NotificationChannelType type(); void deliver(Notification notification); }`
  - `interface PushGateway { void send(PushMessage message); }` com `record PushMessage(UUID userId, String title, String body)` — **o contrato que o app Android implementará (Fase 7)**; `LoggingPushGateway` (`@Component` `@ConditionalOnMissingBean(PushGateway.class)`) só registra em log. `PushChannel implements NotificationChannel` delega ao gateway.
  - `NotificationDispatcher.dispatch(Notification n)`: para cada `NotificationChannel` cujo canal esteja habilitado para `n.userId()`, entrega **uma vez por (eventId, userId, canal)** registrando em `notification_deliveries` (`UNIQUE (event_id, user_id, channel)`, `delivered_at`) **depois** de a entrega ter sucesso; se um canal lançar exceção, os canais já entregues continuam registrados e a exceção propaga (reentrega tenta só os pendentes).
  - `TransferCompletedHandler implements EventHandler` (`consumerName()="notifications"`, `eventType()=TransferCompletedEvent.TYPE`): gera `TRANSFER_SENT` para o remetente (nome do destinatário, valor `R$ 100,00`, data) e `TRANSFER_RECEIVED` para o destinatário (nome do remetente, valor, data), via `UserRepository`; se algum usuário não existir, registra log e descarta o evento (sem exceção).

- [x] **Step 1: Write the failing tests** (canal `RecordingChannel` extra de teste e `PushGateway` de captura; evento construído manualmente e entregue com `handler.handle(event)`): (a) `bothSenderAndReceiverGetPush` com títulos/corpos corretos e sem CPF/e-mail/saldo no texto; (b) `emailChannelOnlyWhenPreferenceEnabled`; (c) `redeliveryDoesNotDuplicateNotifications` (mesmo evento 2x → 1 push por usuário); (d) `failureInOneChannelKeepsDeliveredChannelsAndRetriesOnlyPending` (EMAIL falha na 1ª vez; reentrega → PUSH não repete, EMAIL entrega); (e) `unknownUserIsDroppedWithoutException`.
- [x] **Step 2: Run** `./mvnw test -Dtest=TransferNotificationIntegrationTest` — Expected: FAIL.
- [x] **Step 3: Implement** as classes acima; o dispatcher injeta `List<NotificationChannel>`; formatação BRL em pt-BR e data no fuso `America/Sao_Paulo`.
- [x] **Step 4: Run** `./mvnw test` — Expected: verde.
- [x] **Step 5: Commit** `feat: add notification core with push gateway and idempotent delivery`

---

### Task 8: Canal de e-mail e ponta a ponta

**Files:**
- Create: `B/notification/channel/EmailChannel.java`
- Test: `src/test/java/ElFabrica/Wallet_pay/notification/EmailChannelIntegrationTest.java`, `src/test/java/ElFabrica/Wallet_pay/notification/TransferNotificationEndToEndTest.java`

**Interfaces:**
- Consumes: `EmailSender` (Task 2), `NotificationChannel` (Task 7), `UserRepository` (e-mail do usuário).
- Produces: `EmailChannel implements NotificationChannel` (`type()=EMAIL`): assunto **fixo** por `kind` (ex.: `"Você recebeu uma transferência"` / `"Transferência enviada"`), corpo com o texto da `Notification` (nome do outro usuário apenas no corpo).

- [x] **Step 1: Write the failing tests:** `EmailChannelIntegrationTest`: (a) `deliversToUsersEmailThroughSmtp` — mensagem aparece no Mailpit com `to` = e-mail do usuário; (b) `userNameWithLineBreaksAndHtmlCannotBreakEmail` — nome `"Bob\r\nBcc: x@y.com <b>"` não altera cabeçalhos (assunto fixo, nenhum destinatário extra) e o corpo não contém quebra de cabeçalho. `TransferNotificationEndToEndTest` (broker, job e SMTP reais): (c) `transferNotifiesBothByPushAndEmailsOnlyOptedInUser` — destinatário habilita e-mail via `PUT /notifications/preferences/EMAIL`; remetente não; após `POST` da transferência, executar `OutboxPublisherJob.publishPending()` e aguardar: ambos têm push capturado, só o destinatário tem e-mail no Mailpit; (d) `redeliveredEventDoesNotSendSecondEmail` — republicar o mesmo evento mantém 1 e-mail.
- [x] **Step 2: Run** `./mvnw test -Dtest='EmailChannelIntegrationTest,TransferNotificationEndToEndTest'` — Expected: FAIL.
- [x] **Step 3: Implement** `EmailChannel`; nos testes ponta a ponta limpar Mailpit e filas no `@BeforeEach` e usar `Awaitility` (ou polling manual com timeout de 10 s) para aguardar o consumo.
- [x] **Step 4: Run** `./mvnw test` — Expected: suíte toda verde.
- [x] **Step 5: Commit** `feat: add email notification channel and end-to-end flow`

---

### Task 9: Documentação e KB

**Files:**
- Modify: `kb/02-andamento/estado-atual.md`, `kb/01-produto/roadmap.md`, `kb/05-proximos-passos/proximos-passos.md`, `kb/05-proximos-passos/fase-3-eventos-e-notificacoes.md`, `kb/04-pendencias/pendencias-e-divida-tecnica.md`, `kb/07-guias/como-rodar-e-testar.md`, `ReadMe.md`
- Modify: este plano (marcar checkboxes concluídos)

- [x] **Step 1: Atualizar a KB:** Fase 2 já em `dev` (PRs #1 e #2); Fase 3 (MVP de notificações) entregue; D2 decidida (RabbitMQ atrás de porta), D3 parcial (SMTP/Mailpit em dev; provedor de produção em aberto); contagem de testes da suíte; novos serviços do compose e variáveis (`SPRING_RABBITMQ_*`, `SPRING_MAIL_*`, `WALLETPAY_EMAIL_FROM`); pendências restantes (histórico/DLQ consultável, métricas, push real na Fase 7, migrar os `Logging*Sender` de verificação/recuperação para `EmailSender`).
- [x] **Step 2: Run** `./mvnw test` — Expected: BUILD SUCCESS; anotar o total de testes na KB.
- [x] **Step 3: Commit** `docs: update kb after notifications phase`

---

## Self-Review

- **Cobertura da spec:** push para remetente e destinatário (T7); e-mail opcional (T6, T8); resiliência/outbox (T3, T5); abstração de broker + DLQ (T3, T4); e-mail compartilhável (T2); preferências por usuário (T6); Mailpit/Testcontainers (T1); conteúdo sem dados sensíveis (T7, T8). Fora de escopo preservado: sem migração dos `Logging*Sender`, sem histórico/DLQ por API, sem push real.
- **Consistência de tipos:** `DomainEvent`, `EventPublisher`, `EventHandler` (T3) usados em T4, T5, T7; `NotificationChannelType` (T6) usado em T7/T8; `Notification`/`NotificationChannel` (T7) usados em T8.
- **Decisões do plano (não da spec):** `PUSH` não alterável pelo endpoint (400); `PUT /notifications/preferences/{channel}`; um `PushGateway` além do `PushChannel`; tabela `notification_deliveries` para idempotência por (evento, usuário, canal).
