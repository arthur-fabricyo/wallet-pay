# Estado atual do projeto

Atualizado em **2026-11-04**.

## Situação em uma tabela

| Item | Estado |
|---|---|
| Fase 1 (núcleo da carteira) | ✅ Implementada |
| Fase 2 (segurança e confiabilidade) | ✅ Implementada e em `dev` (PRs #1 e #2) |
| Fase 3 (notificações de transferência — MVP) | ✅ Implementada na branch `feat/fase-3-notificacoes` (PR para `dev` pendente de revisão) |
| Branch de trabalho | `feat/fase-3-notificacoes` (a partir de `dev`) |
| Integração em `main` | ⚠️ Fases 1 e 2 estão em `dev`; `dev` → `main` ainda não foi feito |
| Testes | **293** testes, suíte verde (`./mvnw test`, Docker ligado) |
| Migrations Flyway | V1 a V13 (V11 outbox, V12 preferências, V13 entregas) |
| Plano `2026-10-15-fases-1-2.md` | 12 tasks, todas implementadas segundo o histórico git, mas os checkboxes do arquivo continuam todos `- [ ]` |
| Próxima fase | Fase 4 — motor de automações |

## O que a API entrega hoje

- **Usuários/Auth:** cadastro (CPF com dígitos verificadores; CNPJ via CNPJ.ws), carteira BRL criada junto, verificação
  de e-mail obrigatória, login JWT (access 20 min + refresh 7 dias), refresh, logout, recuperação de senha.
- **Carteira:** saldo, depósito simulado, extrato paginado (filtros por período/tipo, ordenação), limites (consulta e redução).
- **Transações:** transferência interna entre carteiras e comprovante em JSON.
- **Notificações (Fase 3):** transferência concluída grava o evento `TransferCompleted` no outbox (mesma transação); um job
  publica no RabbitMQ (atrás de portas `EventPublisher`/`EventHandler`, trocáveis por Kafka); remetente e destinatário recebem
  **push** (porta `PushGateway`, hoje só log) e, se habilitaram, **e-mail** (SMTP). Preferências por usuário em
  `GET/PUT /notifications/preferences`.
- **Confiabilidade:** lock otimista com retentativa, `Idempotency-Key` obrigatório em dinheiro, auditoria
  (`audit_events` + Envers), limites globais/diários/mensais/noturnos/personalizados, catálogo único de erros e `X-Request-Id`.

Lista completa de rotas: [api.md](../03-arquitetura/api.md).

## Limitações conhecidas hoje

- E-mails de verificação e recuperação **ainda são simulados**: o token é escrito no log (`LoggingEmailVerificationSender`,
  `LoggingPasswordRecoverySender`). O `EmailSender` compartilhado já existe; falta migrá-los.
- Push é um stub (`LoggingPushGateway`): a entrega real vem com o app Android (Fase 7).
- Só a transferência gera evento; depósito e demais tipos ainda não.
- Sem histórico de notificações nem DLQ consultável por API; sem métricas.
- Sem rate limiting (previsto em `core.md` §5, não coberto por nenhuma spec).
- Sem CI/CD, métricas ou dashboards.
- Uma carteira por usuário, apenas BRL; sem estorno; todas as transações criadas são `COMPLETED` (o banco aceita `FAILED`, mas nada o grava; `PENDING` não passa no check do banco).
- Detalhes e prioridades: [pendências e dívida técnica](../04-pendencias/pendencias-e-divida-tecnica.md).

## Stack

Java 21 (alvo; o build local usa o JBR do IntelliJ, ver [guia](../07-guias/como-rodar-e-testar.md)), Spring Boot 4.1.0,
Spring Security 7 (resource server JWT), Spring Data JPA/Hibernate 7.4 + Envers, PostgreSQL 16, Flyway, springdoc-openapi 3.1.0,
JUnit 5 + AssertJ + Testcontainers (PostgreSQL, RabbitMQ, Mailpit), Spring AMQP/RabbitMQ, Spring Mail.
