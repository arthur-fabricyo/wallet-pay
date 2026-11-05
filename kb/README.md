# Base de conhecimento — OutPay / Wallet Pay

Documentação viva do projeto: **onde estamos, o que falta e para onde vamos.**
Última atualização: 2026-11-05 (branch `dev`, após o merge da Fase 3).

> Esta KB resume e organiza o que já existe em `docs/` (specs, visão e plano). Em caso de divergência, a spec em
> [`docs/specs`](../docs/specs) e o código são a fonte de verdade; corrija a KB.

## Resumo em 30 segundos

- **O que é:** API backend de uma carteira digital (Java 21, Spring Boot 4, PostgreSQL) feita como projeto de portfólio.
  O diferencial planejado é um **motor de automações financeiras** ("quando X acontecer, faça Y").
- **Onde estamos:** Fases **1** (núcleo da carteira), **2** (segurança e confiabilidade) e o MVP da **3** (eventos e
  notificações de transferência) estão em **`dev`** (PRs #1, #2 e #3). `main` ainda não recebeu nenhuma fase.
- **O que falta:** o restante da Fase 3 (eventos de depósito, histórico, DLQ consultável, métricas), as Fases 4 a 10
  (motor de automações, cofres/orçamentos, Pix simulado, app Android, Open Finance, observabilidade, recursos avançados)
  e as pendências técnicas das fases já feitas.
- **O que fazer agora:** puxar o próximo item da [esteira](05-proximos-passos/esteira.md) — lista única e ordenada com
  pré-requisitos, fechamento da Fase 3 e fatias da Fase 4.

## Índice

| Pasta | Conteúdo |
|---|---|
| [01-produto](01-produto/) | [Visão do produto](01-produto/visao.md) e [roadmap com status por fase](01-produto/roadmap.md) |
| [02-andamento](02-andamento/) | [Estado atual](02-andamento/estado-atual.md), [linha do tempo](02-andamento/linha-do-tempo.md), detalhe da [Fase 1](02-andamento/fase-1-nucleo-da-carteira.md) e da [Fase 2](02-andamento/fase-2-seguranca-e-confiabilidade.md) |
| [03-arquitetura](03-arquitetura/) | [Visão geral e pacotes](03-arquitetura/visao-geral.md), [operações de saldo](03-arquitetura/operacoes-de-saldo.md), [modelo de dados](03-arquitetura/modelo-de-dados.md), [API](03-arquitetura/api.md), [erros](03-arquitetura/erros.md), [testes](03-arquitetura/testes.md) |
| [04-pendencias](04-pendencias/) | [Pendências e dívida técnica](04-pendencias/pendencias-e-divida-tecnica.md) e [backlog por fase](04-pendencias/backlog-por-fase.md) |
| [05-proximos-passos](05-proximos-passos/) | **[Esteira de trabalho](05-proximos-passos/esteira.md)** (ordem de execução), [plano de próximos passos](05-proximos-passos/proximos-passos.md) (visão e decisões abertas) e [Fase 3 detalhada](05-proximos-passos/fase-3-eventos-e-notificacoes.md) |
| [06-decisoes](06-decisoes/) | [Registro de decisões técnicas](06-decisoes/decisoes-tecnicas.md) |
| [07-guias](07-guias/) | [Como rodar e testar](07-guias/como-rodar-e-testar.md) |

## Mapa para os documentos originais

| Documento | Papel |
|---|---|
| [`ReadMe.md`](../ReadMe.md) | Apresentação pública, endpoints, como rodar |
| [`docs/core.md`](../docs/core.md) | Visão completa do produto e roadmap de 10 fases |
| [`docs/specs/01..15`](../docs/specs) | Especificação de cada funcionalidade (01–09 = Fase 1; 10–15 = Fase 2) |
| [`docs/superpowers/plans/2026-10-15-fases-1-2.md`](../docs/superpowers/plans/2026-10-15-fases-1-2.md) | Plano de implementação das specs 10–15 (12 tasks) |
| [`docs/superpowers/specs/2026-11-04-notificacoes-design.md`](../docs/superpowers/specs/2026-11-04-notificacoes-design.md) | Design da Fase 3 (notificações) — modelo de design doc das fases seguintes |
| [`docs/superpowers/plans/2026-11-04-notificacoes.md`](../docs/superpowers/plans/2026-11-04-notificacoes.md) | Plano de implementação da Fase 3 |
| [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | CI: `./mvnw -B verify` |
| [`docs.md`](../docs.md) | Orientação geral (o documento do usuário é sempre `document`) |

## Convenções desta KB

- Português; um assunto por arquivo; links relativos.
- Afirmações sobre o estado do código vêm do repositório; o que é **recomendação** está marcado como tal.
- Ao concluir um item da [esteira](05-proximos-passos/esteira.md) ou uma fase, atualizar: a esteira,
  [roadmap](01-produto/roadmap.md), [estado atual](02-andamento/estado-atual.md),
  [linha do tempo](02-andamento/linha-do-tempo.md) e mover pendências resolvidas.
- Achado novo (revisão, leitura de código): registrar em [pendências](04-pendencias/pendencias-e-divida-tecnica.md) e na
  esteira — não deixar só em arquivos locais fora do git (ex.: `.superpowers/`).
