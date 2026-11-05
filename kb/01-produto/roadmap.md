# Roadmap e status por fase

Roadmap original em [`docs/core.md` §8](../../docs/core.md). Legenda: ✅ feito · 🟡 parcial · ⬜ não iniciado.

| Fase | Tema | Status | Observação |
|---|---|---|---|
| 1 | Núcleo da carteira | ✅ | Specs 01–09. Ver [detalhe](../02-andamento/fase-1-nucleo-da-carteira.md) |
| 2 | Segurança e confiabilidade | ✅ | Specs 10–15. Ver [detalhe](../02-andamento/fase-2-seguranca-e-confiabilidade.md). Já em `dev` (PRs #1 e #2); falta `dev` → `main` |
| 3 | Eventos e notificações | 🟡 | **MVP entregue e em `dev` (PR #3):** outbox, RabbitMQ atrás de portas, retentativas/DLQ, push (stub) e e-mail opcional para transferência. Faltam depósito/outros eventos, histórico, DLQ consultável e métricas. Ver [detalhe](../05-proximos-passos/fase-3-eventos-e-notificacoes.md) |
| 4 | Motor de automações | ⬜ | **Diferencial do produto.** Depende dos eventos da Fase 3. Escopo mínimo (D6) em discussão; pré-requisitos e fatias na [esteira](../05-proximos-passos/esteira.md) |
| 5 | Organização financeira | ⬜ | Cofres/metas, orçamentos, alertas, recorrentes |
| 6 | Pagamentos simulados | ⬜ | Pix simulado, QR Code, webhook idempotente |
| 7 | Aplicativo Android | ⬜ | Kotlin + Jetpack Compose |
| 8 | Integração bancária experimental | ⬜ | Sandbox/Open Finance atrás de `BankingProvider` |
| 9 | Infraestrutura e observabilidade | 🟡 | Docker Compose da infraestrutura (Postgres, RabbitMQ, Mailpit), CI no GitHub Actions, `X-Request-Id` e log com correlação. Sem Dockerfile da aplicação, CD, métricas, dashboards |
| 10 | Funcionalidades avançadas | ⬜ | Assistente, modelos de automação, carteiras compartilhadas, cartões |

## Escopo de cada fase pendente (resumo)

- **Fase 3** — RabbitMQ; eventos `MoneyReceived`, `TransferCompleted`, `BalanceUpdated`; notificações assíncronas;
  retentativas e fila de mensagens não processadas; métricas e logs.
- **Fase 4** — cadastro de regras; gatilhos/condições/ações; agendamento; histórico de execução; proteção contra
  duplicidade; simulação antes de ativar.
- **Fase 5** — cofres e metas; orçamentos por categoria; alertas de limite; despesas recorrentes.
- **Fase 6** — cobrança Pix simulada; QR Code; webhook; confirmação idempotente; saldo + eventos.
- **Fase 7** — login/cadastro, home, extrato, transferências, cofres, automações.
- **Fase 8** — definir caso de uso; escolher provedor; sandbox; adaptador/normalização; revogação de consentimento.
- **Fase 9** — Compose completo, CI/CD, métricas (Micrometer/Prometheus/Grafana), alertas.
- **Fase 10** — assistente financeiro, modelos compartilháveis, carteiras compartilhadas, cartões virtuais, Open Finance.

Para tarefas concretas por fase, ver [backlog por fase](../04-pendencias/backlog-por-fase.md); para a ordem de execução,
a [esteira](../05-proximos-passos/esteira.md).
