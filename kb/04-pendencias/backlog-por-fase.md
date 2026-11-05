# Backlog por fase

O que falta construir, fase a fase. Fonte: [`docs/core.md`](../../docs/core.md) §3–§8.
Pendências das fases já entregues estão em [pendencias-e-divida-tecnica.md](pendencias-e-divida-tecnica.md).
A **ordem de execução** de tudo isso está na [esteira](../05-proximos-passos/esteira.md).

Desde a Fase 3, cada fase é descrita por um design doc (`docs/superpowers/specs/AAAA-MM-DD-<tema>-design.md`) e um plano
(`docs/superpowers/plans/`), não mais por specs numeradas em `docs/specs` (que param na 15).

## Fase 3 — Eventos e notificações 🟡 (MVP entregue em 2026-11-04)

- [x] RabbitMQ no `docker-compose.yml` e na aplicação (atrás das portas `EventPublisher`/`EventHandler`).
- [x] *Transactional Outbox*: evento gravado na mesma transação da operação; publicador agendado lê a outbox e espera a
  confirmação do broker.
- [ ] Eventos de domínio: só `TransferCompleted` existe. Faltam `MoneyReceived`/`BalanceUpdated` e o evento de depósito
  (esteira A05).
- [x] Consumidores idempotentes: entrega deduplicada por (evento, usuário, canal) em `notification_deliveries`.
  A tabela genérica de eventos processados por (consumidor, evento) da proposta original não foi feita.
- [x] Notificações de transferência enviada/recebida; preferências por canal (push sempre ligado, e-mail opcional).
- [ ] Histórico de notificações; preferências por tipo e frequência (esteira B02, B03).
- [x] E-mail SMTP compartilhado (`EmailSender`), testado com Mailpit.
- [ ] Migrar os `Logging*Sender` de verificação/recuperação para o `EmailSender` (esteira B04).
- [x] Retentativas com backoff no consumidor e DLQ por fila.
- [ ] DLQ consultável/reprocessável e outbox resiliente a linha venenosa (esteira A01, A02).
- [ ] Métricas e logs estruturados de eventos (esteira A08).
- [x] Testes de integração com broker (Testcontainers: PostgreSQL, RabbitMQ, Mailpit compartilhados na JVM).

## Fase 4 — Motor de automações ⬜ (diferencial)

- [ ] Modelo de regra: gatilho + condições + ações + frequência + limites; estados (ativa, pausada, excluída).
- [ ] Avaliador de regras consumindo eventos da Fase 3.
- [ ] Agendamento (regras por data/recorrência).
- [ ] Idempotência por (regra, evento) — evitar execução duplicada.
- [ ] Revalidar a ação antes de executar (saldo, limites da spec 14).
- [ ] Execuções devem passar pelo [padrão de operações de saldo](../03-arquitetura/operacoes-de-saldo.md).
- [ ] Histórico de execuções, falhas e auditoria (`AuditActorType.SYSTEM` existe no enum, mas o `AuditLogger` ainda não o
  grava — pendência #30).
- [ ] Simulação ("dry-run") antes de ativar; confirmação do usuário quando exigida.
- [ ] Ações do tipo notificar (alerta) e transferir; depois reservar em cofre (Fase 5).
- [ ] Proteção contra cascata: operação feita por automação precisa ser identificável no evento (pendência #29).

Pré-requisitos e fatias propostas: blocos A e F4 da [esteira](../05-proximos-passos/esteira.md).

## Fase 5 — Organização financeira ⬜

- [ ] Cofres/metas: valor-alvo, prazo, aportes, saldo reservado × disponível, sugestão de contribuição.
- [ ] Orçamentos por categoria (exige categorizar transações), alertas a 80%/100%, bloqueio opcional.
- [ ] Despesas recorrentes e alertas de vencimento.
- [ ] Integrar cofres e orçamentos ao motor de automações.

## Fase 6 — Pagamentos simulados (Pix) ⬜

- [ ] Cobrança Pix simulada e QR Code.
- [ ] Webhook simulado com validação de assinatura, idempotência e eventos fora de ordem.
- [ ] Nova origem de transações `DEPOSIT`; ajustar check de status para fluxos assíncronos (ver pendência 14).
- [ ] Publicar eventos ao confirmar o pagamento.

## Fase 7 — Aplicativo Android ⬜

- [ ] Kotlin + Jetpack Compose, ViewModel, Coroutines, Retrofit.
- [ ] Login/cadastro, home e saldo, extrato, transferência, cofres/metas, automações.
- [ ] Contrato da API estável e documentado (OpenAPI já existe).

## Fase 8 — Integração bancária experimental ⬜

- [ ] Definir o caso de uso (leitura × iniciação de pagamento).
- [ ] Interface `BankingProvider` + `SandboxBankingProvider`.
- [ ] Consentimento, revogação, expiração de tokens, normalização de erros e estados.

## Fase 9 — Infraestrutura e observabilidade 🟡 (parte antecipada na esteira, bloco G)

- [x] Docker Compose da infraestrutura (Postgres, RabbitMQ, Mailpit); `X-Request-Id` e correlação nos logs.
- [ ] Aplicação no Compose e Dockerfile.
- [x] CI: `./mvnw -B verify` no GitHub Actions (push para `main` e PRs).
- [ ] CI em push para `dev`, cobertura (JaCoCo), análise estática e CD.
- [ ] Micrometer + Prometheus + Grafana; alertas e rastreamento de erros.

## Fase 10 — Avançadas ⬜

- [ ] Assistente financeiro (informativo, sem executar movimentações sem autorização).
- [ ] Modelos de automação compartilháveis; pontuação educativa.
- [ ] Carteiras compartilhadas com permissões e aprovação.
- [ ] Cartões virtuais (depende de parceiro emissor); Open Finance conforme viabilidade.
