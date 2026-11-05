# Linha do tempo

Reconstruída a partir do histórico git (`git log`). Datas no formato AAAA-MM-DD.

## Julho–agosto de 2026 — Fase 1 (núcleo da carteira)

| Data | Marco | Commit |
|---|---|---|
| 07-31 | Início do repositório e README | `d8a6e81` |
| 08-06 | Primeiras specs do projeto | `11a5dc8`, `1cc93b9` |
| 08-08 | Login (JWT + refresh) | `e3f1530` |
| 08-10 | Rota da carteira (`/wallets/me`) e mais specs | `525ac2c`, `a8ea3b7` |
| 08-11 | Verificação de e-mail; reorganização de pastas no padrão de mercado; DTOs em todos os controllers | `1c31c9f`, `4168191`, `ac0ccfe` |
| 08-12 | Recuperação de senha; testes de casos adicionais | `28ad23c`, `5cb9627` |
| 08-13 | Transferência entre carteiras | `e65e39f` |
| 08-26 | Transferência (ajustes) e extrato da própria carteira | `73c7e1d`, `b2692e7` |

## Outubro de 2026 — Fase 2 (segurança e confiabilidade)

| Data | Marco | Commit |
|---|---|---|
| 10-15 | Specs 10–15 escritas e plano de implementação (12 tasks) | `e5e0be5`, `e00254e` |
| 10-20 | Task 1 — infraestrutura de testes com Testcontainers | `fe78ffb` |
| 10-20 | Task 2 — catálogo de erros e handler único | `9cf39d1` |
| 10-20 | Task 3 — `X-Request-Id`, 401/403 em JSON, timeouts do CNPJ | `bacff20` |
| 10-20 | Task 4 — classe `Money` (+ guarda de overflow) | `dab8de5`, `8bd3903` |
| 10-20 | Task 5 — lock otimista + retentativa (spec 10) | `a8cfe2c` |
| 10-20 | Task 6 — depósito simulado (spec 11) | `1be9378` |
| 10-20 | Task 7 — idempotência (spec 12) | `fa9cc56` |
| 10-20 | Task 8 — eventos de auditoria (spec 13A) | `127d666` |
| 10-20 | Task 9 — histórico com Envers (spec 13B) | `628c0ff` |
| 10-20 | Task 10 — limites aplicados (spec 14) | `11e3bd1` |
| 10-20 | Task 11 — endpoints de limites | `3660e96` |
| 10-20 | Task 12 — testes ponta a ponta (spec 15) | `a76f380` |
| 10-20 | Correção de deadlock em transferências cruzadas (`order_updates`) | `64647ae` |
| 10-28 | Correções pós-revisão: login falho fora da transação, truncar valores rejeitados na auditoria, prefixo de propriedade do Envers, validação de limites (sub-centavo e janela noturna vazia), clarificação de teste | `9063138`, `7d14d51`, `705af23`, `75119f5`, `ea6df05` |
| 10-28 | README descreve o que a API entrega | `87763af` |

## Novembro de 2026 — Integração e Fase 3 (eventos e notificações)

| Data | Marco | Commit |
|---|---|---|
| 11-03 | KB criada; Fases 1 e 2 mergeadas em `dev` (PRs #1 e #2) | `3f067cd`, `5b9565f`, `33b875c` |
| 11-04 | Design e plano da Fase 3 | `254e7a9` |
| 11-04 | RabbitMQ e Mailpit no Compose; `EmailSender` SMTP compartilhado | `a3ea9d9`, `2c51f01` |
| 11-04 | Outbox transacional e contratos de evento; adaptador RabbitMQ | `a2b9608`, `00cc943` |
| 11-04 | Evento `TransferCompleted` na transação da transferência | `629b875` |
| 11-04 | Preferências de notificação; núcleo de notificações com push (stub) e entrega idempotente | `f126053`, `5ef7d37` |
| 11-04 | Containers de teste compartilhados e namespace do broker por contexto; canal de e-mail e fluxo ponta a ponta | `ded139f`, `ecd9767` |
| 11-05 | Correções da revisão final: falha de um canal não impede os outros; outbox espera confirmação do broker; broker e e-mail fora do health | `4b4ce9e`, `8b0cc1f`, `8a0bf89` |
| 11-05 | Pendências registradas na KB; CI no GitHub Actions; Fase 3 mergeada em `dev` (PR #3) | `ff1fb96`, `5a1bef3`, `5b2c760` |
| 11-05 | KB revisada contra o código e criada a [esteira](../05-proximos-passos/esteira.md) de trabalho | — |
| 11-05 | Esteira bloco 0: CI também em push para `dev` (E01); plano das Fases 1–2 concluído e specs alinhadas (E03) | `5118947` |

## Hoje (2026-11-05)

Fases 1 a 3 em `dev`. Próximo trabalho definido pela [esteira](../05-proximos-passos/esteira.md): base de eventos, fechamento
da Fase 3 e motor de automações (Fase 4).
