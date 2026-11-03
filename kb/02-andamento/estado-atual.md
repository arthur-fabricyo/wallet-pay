# Estado atual do projeto

Atualizado em **2026-11-03**.

## Situação em uma tabela

| Item | Estado |
|---|---|
| Fase 1 (núcleo da carteira) | ✅ Implementada |
| Fase 2 (segurança e confiabilidade) | ✅ Implementada |
| Branch de trabalho | `feat/fases-1-2`, working tree limpo, sincronizada com `origin/feat/fases-1-2` |
| Integração em `main` | ⚠️ **Pendente** — a branch está 22 commits à frente de `main` (152 arquivos, +18.347 / −456 linhas) |
| Último commit | `87763af` (2026-10-28) — README descreve o que a API entrega |
| Testes | 217 métodos `@Test` em 37 classes (contagem estática; a suíte não foi executada para escrever esta KB) |
| Migrations Flyway | V1 a V10 |
| Plano `2026-10-15-fases-1-2.md` | 12 tasks, todas implementadas segundo o histórico git, mas os checkboxes do arquivo continuam todos `- [ ]` |
| Próxima fase | Fase 3 — eventos e notificações (não iniciada) |

## O que a API entrega hoje

- **Usuários/Auth:** cadastro (CPF com dígitos verificadores; CNPJ via CNPJ.ws), carteira BRL criada junto, verificação
  de e-mail obrigatória, login JWT (access 20 min + refresh 7 dias), refresh, logout, recuperação de senha.
- **Carteira:** saldo, depósito simulado, extrato paginado (filtros por período/tipo, ordenação), limites (consulta e redução).
- **Transações:** transferência interna entre carteiras e comprovante em JSON.
- **Confiabilidade:** lock otimista com retentativa, `Idempotency-Key` obrigatório em dinheiro, auditoria
  (`audit_events` + Envers), limites globais/diários/mensais/noturnos/personalizados, catálogo único de erros e `X-Request-Id`.

Lista completa de rotas: [api.md](../03-arquitetura/api.md).

## Limitações conhecidas hoje

- E-mails de verificação e recuperação **são simulados**: o token é escrito no log (`LoggingEmailVerificationSender`,
  `LoggingPasswordRecoverySender`).
- Sem mensageria: tudo é síncrono dentro da requisição.
- Sem rate limiting (previsto em `core.md` §5, não coberto por nenhuma spec).
- Sem CI/CD, métricas ou dashboards.
- Uma carteira por usuário, apenas BRL; sem estorno; todas as transações criadas são `COMPLETED` (o banco aceita `FAILED`, mas nada o grava; `PENDING` não passa no check do banco).
- Detalhes e prioridades: [pendências e dívida técnica](../04-pendencias/pendencias-e-divida-tecnica.md).

## Stack

Java 21 (alvo; o build local usa o JBR do IntelliJ, ver [guia](../07-guias/como-rodar-e-testar.md)), Spring Boot 4.1.0,
Spring Security 7 (resource server JWT), Spring Data JPA/Hibernate 7.4 + Envers, PostgreSQL 16, Flyway, springdoc-openapi 3.1.0,
JUnit 5 + AssertJ + Testcontainers.
