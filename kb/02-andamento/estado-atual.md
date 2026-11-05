# Estado atual do projeto

Atualizado em **2026-11-05**.

## Situação em uma tabela

| Item | Estado |
|---|---|
| Fase 1 (núcleo da carteira) | ✅ Implementada |
| Fase 2 (segurança e confiabilidade) | ✅ Implementada e em `dev` (PRs #1 e #2) |
| Fase 3 (notificações de transferência — MVP) | ✅ Implementada e em `dev` (PR #3, 2026-11-05); itens restantes nos blocos A e B da [esteira](../05-proximos-passos/esteira.md) |
| Branch principal de trabalho | `dev` (features em `feat/…` a partir dela) |
| Integração em `main` | ⚠️ Fases 1 a 3 estão em `dev`; `dev` → `main` ainda não foi feito (D1) |
| CI | GitHub Actions (`.github/workflows/ci.yml`): `./mvnw -B verify` em push para `main` e em PRs; não roda em push para `dev` |
| Testes | **296** testes, suíte verde (`./mvnw test`, Docker ligado) |
| Migrations Flyway | V1 a V13 (V11 outbox, V12 preferências, V13 entregas); próxima: V14 |
| Plano `2026-10-15-fases-1-2.md` | 12 tasks, todas implementadas segundo o histórico git, mas os checkboxes do arquivo continuam todos `- [ ]` |
| O que fazer agora | Ver a [esteira](../05-proximos-passos/esteira.md): base de eventos e fechamento da Fase 3, depois o motor de automações (Fase 4) |

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
- Só a transferência gera evento; depósito e demais tipos ainda não. O evento não indica se a operação veio do usuário ou
  de uma automação.
- Sem histórico de notificações nem DLQ consultável por API; uma linha inválida no outbox trava a publicação.
- Sem rate limiting (previsto em `core.md` §5, não coberto por nenhuma spec).
- Sem CD, métricas ou dashboards (CI existe).
- Agendador do Spring com uma única thread; auditoria não consegue gravar ator `SYSTEM`.
- Uma carteira por usuário, apenas BRL; sem estorno; todas as transações criadas são `COMPLETED` (o banco aceita `FAILED`, mas nada o grava; `PENDING` não passa no check do banco).
- Detalhes e prioridades: [pendências e dívida técnica](../04-pendencias/pendencias-e-divida-tecnica.md).

## Stack

Java 21 (alvo; o build local usa o JBR do IntelliJ, ver [guia](../07-guias/como-rodar-e-testar.md)), Spring Boot 4.1.0,
Spring Security 7 (resource server JWT), Spring Data JPA/Hibernate 7.4 + Envers, PostgreSQL 16, Flyway, springdoc-openapi 3.1.0,
JUnit 5 + AssertJ + Testcontainers (PostgreSQL, RabbitMQ, Mailpit), Spring AMQP/RabbitMQ, Spring Mail.
