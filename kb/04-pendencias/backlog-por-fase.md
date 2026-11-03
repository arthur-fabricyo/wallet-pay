# Backlog por fase

O que falta construir, fase a fase. Fonte: [`docs/core.md`](../../docs/core.md) §3–§8.
Pendências das fases já entregues estão em [pendencias-e-divida-tecnica.md](pendencias-e-divida-tecnica.md).
Specs das fases futuras **ainda não existem** (próxima numeração livre: 16).

## Fase 3 — Eventos e notificações ⬜ (próxima)

- [ ] RabbitMQ no `docker-compose.yml` e na aplicação.
- [ ] *Transactional Outbox*: gravar o evento na mesma transação da operação financeira; publicador lê a outbox.
- [ ] Eventos de domínio: `MoneyReceived`, `TransferCompleted`, `BalanceUpdated` (depósito e transferência passam a emitir).
- [ ] Consumidores idempotentes (reentrega é normal em mensageria).
- [ ] Notificações: transferência enviada/recebida; histórico; preferências de canal e frequência.
- [ ] Provedor real (ou sandbox) de e-mail substituindo os `Logging*Sender`.
- [ ] Retentativas com backoff e fila de mensagens não processadas (DLQ).
- [ ] Métricas e logs estruturados de eventos.
- [ ] Testes de integração com broker (Testcontainers).

## Fase 4 — Motor de automações ⬜ (diferencial)

- [ ] Modelo de regra: gatilho + condições + ações + frequência + limites; estados (ativa, pausada, excluída).
- [ ] Avaliador de regras consumindo eventos da Fase 3.
- [ ] Agendamento (regras por data/recorrência).
- [ ] Idempotência por (regra, evento) — evitar execução duplicada.
- [ ] Revalidar a ação antes de executar (saldo, limites da spec 14).
- [ ] Execuções devem passar pelo [padrão de operações de saldo](../03-arquitetura/operacoes-de-saldo.md).
- [ ] Histórico de execuções, falhas e auditoria (`AuditActorType.SYSTEM` já existe).
- [ ] Simulação ("dry-run") antes de ativar; confirmação do usuário quando exigida.
- [ ] Ações do tipo notificar (alerta) e transferir; depois reservar em cofre (Fase 5).

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

## Fase 9 — Infraestrutura e observabilidade 🟡

- [x] Docker Compose do Postgres; `X-Request-Id` e correlação nos logs.
- [ ] Compose completo (app + Postgres + RabbitMQ …) e Dockerfile.
- [ ] CI/CD (build, testes com Docker, análise).
- [ ] Micrometer + Prometheus + Grafana; alertas e rastreamento de erros.

## Fase 10 — Avançadas ⬜

- [ ] Assistente financeiro (informativo, sem executar movimentações sem autorização).
- [ ] Modelos de automação compartilháveis; pontuação educativa.
- [ ] Carteiras compartilhadas com permissões e aprovação.
- [ ] Cartões virtuais (depende de parceiro emissor); Open Finance conforme viabilidade.
