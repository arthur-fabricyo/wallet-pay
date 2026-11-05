# Pendências e dívida técnica

Itens abertos das Fases 1, 2 e 3. Prioridade: 🔴 alta · 🟡 média · 🟢 baixa · ✅ resolvido.
Origem: "fora de escopo" das specs, `docs/core.md` §5, revisões finais das fases e leitura do código em 2026-11-05.
A **ordem de execução** destes itens está na [esteira](../05-proximos-passos/esteira.md).

## Fechamento da Fase 2

| # | Item | Prio | Detalhe |
|---|---|---|---|
| 1 | Mergear `feat/fases-1-2` | 🟡 | ✅ Em `dev` desde 2026-11-03 (PRs #1 e #2). Falta `dev` → `main` (decisão D1) |
| 2 | Atualizar o plano `2026-10-15-fases-1-2.md` | 🟢 | 105 checkboxes ainda `- [ ]` apesar de tudo implementado; marcar ou mover para "concluídos" |
| 3 | Executar a suíte completa e registrar o resultado | ✅ | 296 testes verdes em 2026-11-04 (`./mvnw test`) |
| 4 | Revisar specs vs. implementação | 🟢 | O plano ajustou a spec 15 (códigos de token). Refletir os 11 ajustes de [decisões](../06-decisoes/decisoes-tecnicas.md) nas specs |

## Segurança

| # | Item | Prio | Detalhe |
|---|---|---|---|
| 5 | Rate limiting / proteção contra força bruta | 🔴 | Previsto em `core.md` §5; nenhuma spec o cobre. Login, recuperação de senha e reenvio de e-mail são públicos |
| 6 | Bloqueio por tentativas de login | 🟡 | Fora de escopo das specs 02/04/05 |
| 7 | `JWT_SECRET` padrão de desenvolvimento | 🟡 | O valor default está no `application.properties`; exigir variável em perfis não locais (falhar na subida) |
| 8 | 2FA e device fingerprint | 🟢 | Fora de escopo da spec 02 |
| 9 | Autorização reforçada para operações sensíveis | 🟡 | `core.md` §5; ex.: confirmação para transferências acima de um valor |
| 10 | `X-Forwarded-For` não confiado na auditoria | 🟢 | Ao colocar a API atrás de proxy/gateway, configurar IP real (hoje grava o IP do proxy) |

## Funcional

| # | Item | Prio | Detalhe |
|---|---|---|---|
| 11 | Migrar verificação/recuperação de senha para o `EmailSender` | 🟡 | `EmailSender` SMTP compartilhado já existe (Fase 3); `LoggingEmailVerificationSender`/`LoggingPasswordRecoverySender` ainda só escrevem em log. Provedor transacional de produção também em aberto |
| 11b | Histórico de notificações, DLQ consultável, métricas de eventos | 🟡 | Fora do MVP da Fase 3 |
| 11c | Push real | 🟡 | Contrato `PushGateway` pronto; implementação (FCM) na Fase 7 |
| 11d | Retenção de `outbox_events` e `notification_deliveries` | 🟡 | Crescem sem limpeza; manter entregas pelo menos o tempo da janela de reentrega |
| 11e | Linha "venenosa" no outbox e DLQ sem consumidor/alerta | 🟡 | `OutboxPublisherJob` para na primeira falha; sem teto de tentativas nem estado "parked"; DLQ não é reprocessada |
| 11f | Timeouts/autenticação/TLS do SMTP | 🟡 | Sem `mail.smtp.timeout`/`connectiontimeout`; um SMTP travado pode prender a thread do consumidor |
| 11g | `LoggingPushGateway` registra nome e valor em INFO | 🟢 | Dado pessoal em log (LGPD); trocar por userId/DEBUG |
| 12 | Templates finais de e-mail | 🟢 | Specs 04/05 |
| 13 | Estorno e saque | 🟡 | Fora de escopo das specs 06/11; exigem novos tipos de transação, status e auditoria |
| 14 | Status `PENDING` vs. check do banco | 🟡 | `ck_transactions_status` só aceita `COMPLETED`/`FAILED`; ajustar via migration antes de fluxos assíncronos (Fase 6) |
| 15 | Transações `FAILED` não são gravadas | 🟢 | Falhas ficam só em `audit_events`; decidir se o extrato deve mostrá-las |
| 16 | Comprovante em PDF/assinado | 🟢 | Spec 07 |
| 17 | Extrato em CSV/PDF | 🟢 | Spec 08 |
| 18 | Várias carteiras / múltiplas moedas | 🟢 | Hoje 1 carteira BRL por usuário (check no banco). Previsto em `core.md` §3.1 |
| 19 | Aumento de limite com carência/aprovação; limites por destinatário/categoria | 🟢 | Fora de escopo da spec 14 |
| 20 | Endpoint de consulta da auditoria | 🟡 | Spec 13: consulta é só por SQL; sem política de retenção/expurgo nem exportação |
| 21 | Idempotência em cadastro/login/recuperação | 🟢 | Fora de escopo da spec 12 |

## Observabilidade e operação (Fase 9, mas vale antecipar)

| # | Item | Prio | Detalhe |
|---|---|---|---|
| 22 | CI/CD com a suíte rodando | 🟡 | ✅ CI existe (`.github/workflows/ci.yml`, `./mvnw -B verify` em push para `main` e `dev` e em PRs; esteira E01). Falta CD |
| 23 | Métricas (Micrometer), dashboards, alertas | 🟡 | Só existem `X-Request-Id` e log com correlação; nenhum `MeterRegistry` no código |
| 24 | Dockerfile / Compose da aplicação | 🟡 | `docker-compose.yml` sobe Postgres, RabbitMQ e Mailpit; falta a aplicação (Dockerfile) |
| 25 | Cobertura de testes (JaCoCo) | 🟢 | Sem relatório |
| 26 | `JAVA_HOME` não definido na máquina de dev | 🟢 | Build usa o JBR do IntelliJ (ver [guia](../07-guias/como-rodar-e-testar.md)); documentar/definir JDK 21 |
| 27 | Circuit breaker no cliente do CNPJ | 🟢 | Hoje só há timeouts (2 s / 3 s) e `503` |

## Achados da leitura do código (2026-11-05)

Itens que não estavam registrados. Os quatro primeiros afetam diretamente o motor de automações (Fase 4).

| # | Item | Prio | Detalhe |
|---|---|---|---|
| 28 | Outbox segura transação e locks durante o I/O do broker | 🟡 | `OutboxPublisherJob` publica dentro da `TransactionTemplate` que fez `for update skip locked`; ordem é só "melhor esforço" por instância. Achado da revisão final da Fase 3 (antes só no ledger local `.superpowers`) |
| 29 | Evento não diz quem originou a operação | 🔴 | `TransferCompletedPayload` e `transactions` não têm origem/iniciador. Transferência feita por automação será idêntica à manual e pode disparar outras regras em cascata |
| 30 | `AuditActorType.SYSTEM` não é gravável | 🟡 | `AuditLogger.insert` só produz `USER` (com ator) ou `ANONYMOUS`. Em jobs e consumidores não há `SecurityContext` nem IP |
| 31 | Agendador com uma thread só | 🟡 | `SchedulingConfig` é só `@EnableScheduling`, sem `spring.task.scheduling.*`: outbox (2 s), limpeza de idempotência e futuros jobs dividem a mesma thread. Sem ShedLock; o único padrão multi-instância é `for update skip locked` |
| 32 | Achados menores da revisão final da Fase 3 | 🟢 | Usuário desconhecido num lado derruba a notificação do outro lado; `displayName` não remove U+0085/U+2028/U+2029; dois testes usam `Thread.sleep` para provar ausência. Antes só no ledger local `.superpowers` |
| 33 | `Instant.now()` fora do `Clock` | 🟢 | `WalletEntity`, `UserEntity`, `TransactionEntity` (fallback), entidades de token e serviços de auth usam `Instant.now()`, contrariando T22 |
| 34 | Enums aceitam valores que o banco rejeita | 🟢 | `TransactionType` tem `WITHDRAW`/`REFUND`/`FEE` (check só aceita `TRANSFER`/`DEPOSIT`); `TransactionStatus.PENDING` (ver #14) |
| 35 | Validação só no DTO | 🟡 | `CreateTransferUseCase` não valida `receiverUserId` nulo (NPE no hash) nem descrição > 255 (vira `DATA_CONFLICT` sem auditoria). `WalletController.updateMyLimits` não tem `@Valid`. Chamadas internas (motor) não passam pelos DTOs |
| 36 | `ErrorCode.error()` só conhece alguns status | 🟢 | Um código com 429 lançaria `IllegalStateException`; ajustar antes do rate limiting. `ErrorCodeTest` fixa o catálogo exato |
| 37 | Decisões T29 e T31 contrariadas na Fase 3 | 🟢 | `TransferNotificationIntegrationTest` usa `@MockitoSpyBean` (T29: sem Mockito); notificações têm acento (T31: textos sem acento). Decidir e alinhar |
| 38 | Dois prefixos de configuração | 🟢 | `wallet-pay.*` (JWT, CNPJ, TTLs) e `walletpay.*` (limites, eventos, e-mail) |

## Riscos a acompanhar

- **Spring Boot 4 / Spring Framework 7 / Jackson 3 / Hibernate 7:** versões recentes; APIs novas (`org.springframework.core.retry`,
  `tools.jackson`). Cuidado ao seguir tutoriais antigos.
- **Envers será descontinuado no Hibernate 8** (spec 13): o novo `@Audited` do core lê o formato do log do Envers; planejar migração.
- **Auditoria via `UPDATE` em massa não é capturada pelo Envers:** proibido em entidades auditadas.
- **Transações longas na camada interna** crescem com cada nova checagem (limites, automações); medir antes da Fase 4.
