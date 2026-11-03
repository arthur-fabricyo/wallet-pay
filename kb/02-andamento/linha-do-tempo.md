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

## Hoje (2026-11-03)

Fases 1 e 2 prontas na branch; aguardando decisão de merge e definição do início da Fase 3.
