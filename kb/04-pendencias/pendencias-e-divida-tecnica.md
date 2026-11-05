# Pendências e dívida técnica

Itens abertos das Fases 1 e 2. Prioridade: 🔴 alta · 🟡 média · 🟢 baixa.
Origem: "fora de escopo" das specs, `docs/core.md` §5 e o estado do repositório em 2026-11-03.

## Fechamento da Fase 2

| # | Item | Prio | Detalhe |
|---|---|---|---|
| 1 | Mergear `feat/fases-1-2` em `main` | 🔴 | Branch 22 commits à frente; nasceu de `docs/specs-fase-1-2`, que também precisa entrar. Decidir: PR único ou squash |
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
| 22 | CI/CD com a suíte rodando | 🔴 | Não há pipeline; os testes dependem de Docker |
| 23 | Métricas (Micrometer), dashboards, alertas | 🟡 | Só existem `X-Request-Id` e log com correlação |
| 24 | Dockerfile / Compose da aplicação | 🟡 | `docker-compose.yml` sobe apenas o Postgres |
| 25 | Cobertura de testes (JaCoCo) | 🟢 | Sem relatório |
| 26 | `JAVA_HOME` não definido na máquina de dev | 🟢 | Build usa o JBR do IntelliJ (ver [guia](../07-guias/como-rodar-e-testar.md)); documentar/definir JDK 21 |
| 27 | Circuit breaker no cliente do CNPJ | 🟢 | Hoje só há timeouts (2 s / 3 s) e `503` |

## Riscos a acompanhar

- **Spring Boot 4 / Spring Framework 7 / Jackson 3 / Hibernate 7:** versões recentes; APIs novas (`org.springframework.core.retry`,
  `tools.jackson`). Cuidado ao seguir tutoriais antigos.
- **Envers será descontinuado no Hibernate 8** (spec 13): o novo `@Audited` do core lê o formato do log do Envers; planejar migração.
- **Auditoria via `UPDATE` em massa não é capturada pelo Envers:** proibido em entidades auditadas.
- **Transações longas na camada interna** crescem com cada nova checagem (limites, automações); medir antes da Fase 4.
