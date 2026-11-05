# Próximos passos — para onde vamos

> **Status deste documento:** visão por passos (histórico e direção geral). A ordem item a item, que é o que se executa,
> está na **[esteira](esteira.md)**. Decisões abertas estão no final.

## Sequência recomendada

### Passo 0 — Fechar a Fase 2 (curto) — ✅ feito

> A Fase 2 já está em `dev` (PRs #1 e #2) e a suíte está verde. Resta só o `dev` → `main`. O texto abaixo é o histórico.

1. Rodar `./mvnw test` com Docker ligado e confirmar a suíte verde.
2. Abrir PR de `feat/fases-1-2` → `main` (inclui as specs 10–15 e o plano) e mergear.
3. Marcar o plano como concluído; atualizar esta KB.
4. Decidir quais pendências 🔴 de [pendências](../04-pendencias/pendencias-e-divida-tecnica.md) entram antes da Fase 3
   (sugestão: CI com a suíte e falhar na subida sem `JWT_SECRET` fora de dev).

### Passo 1 — Fase 3: eventos e notificações — 🟡 MVP entregue

> Entregue e em `dev` (PR #3, 2026-11-05): outbox + RabbitMQ atrás de portas + notificação de transferência (push stub e e-mail
> opcional). Pendente: depósito e demais eventos, histórico de notificações, DLQ consultável, métricas, push real (Fase 7) e
> migrar os `Logging*Sender` — blocos A e B da [esteira](esteira.md).

Por que agora: o motor de automações (Fase 4, o diferencial) **só faz sentido sobre eventos confiáveis**, e as notificações
são a primeira entrega visível para o usuário. Escopo e riscos em [fase-3-eventos-e-notificacoes.md](fase-3-eventos-e-notificacoes.md).

Fluxo de trabalho usado na Fase 3 (e daqui em diante): design doc em `docs/superpowers/specs`, plano de tasks em
`docs/superpowers/plans`, implementação task a task com um commit por task, PR para `dev`.

### Passo 2 — Fase 4: motor de automações

Entrar com um recorte mínimo e demonstrável: **uma regra** ("quando entrar dinheiro, avisar" e depois "transferir X%") com histórico
de execução, idempotência por (regra, evento) e simulação. Ampliar gatilhos/ações depois. "Reservar" depende dos cofres (Fase 5):
hoje há uma carteira por usuário e transferência para si mesmo é proibida.

Critério do dono do projeto (2026-11-05): **o mais completo possível** — fechar a base de eventos e a Fase 3 antes do motor.
Pré-requisitos e fatias: blocos A, B e F4 da [esteira](esteira.md).

### Passo 3 — Fases 5 e 6 (em paralelo ou em sequência)

- Cofres/metas dão destino às ações do motor (ex.: "separar 15% da entrada").
- Pix simulado dá uma segunda origem realista de entradas e exercita webhook idempotente.

### Passo 4 — Fase 9 em paralelo ao longo do caminho

CI/CD, Dockerfile e métricas devem acompanhar as fases 3–6, não ficar só para o fim.

### Depois — Fases 7, 8 e 10

App Android quando a API das fases 3–6 estabilizar; integração bancária e recursos avançados conforme viabilidade.

## Decisões abertas (precisam do dono do projeto)

| # | Decisão | Opções / contexto | Quando decidir |
|---|---|---|---|
| D1 | Promover `dev` → `main` | Fases 1–3 estão em `dev` (PRs #1, #2, #3); `main` ainda não recebeu nenhuma | Agora (esteira E02) |
| D2 | Broker da Fase 3 | ✅ **Decidido:** RabbitMQ atrás das portas `EventPublisher`/`EventHandler` (troca por Kafka = novo adaptador) | — |
| D3 | Provedor de e-mail | 🟡 **Parcial:** `EmailSender` SMTP compartilhado, testado com Mailpit. Provedor transacional de produção segue em aberto | Antes de produção |
| D4 | Onde fica o rate limiting | Filtro em memória (Bucket4j) × Redis (já previsto para coordenação) × no gateway | Antes de expor a API |
| D5 | Modelo de categorias de transação | Necessário para orçamentos (Fase 5) e condições do motor (Fase 4) | Início da Fase 4 |
| D6 | Escopo mínimo do motor de automações (MVP) | Quais gatilhos/ações entram na primeira versão. 🟡 Em discussão (2026-11-05); proposta na esteira F4 | Antes da Fase 4 |
| D7 | Estratégia de testes com broker | ✅ **Decidido:** Testcontainers (RabbitMQ, Mailpit, PostgreSQL), containers compartilhados na JVM de teste | — |

## Marcos sugeridos

| Marco | Entrega demonstrável |
|---|---|
| M1 | Fase 2 em `main`, CI verde (🟡 em `dev` com CI; falta `main`) |
| M2 | Transferência/depósito emitem evento via outbox; notificação de "dinheiro recebido" chega por e-mail real/sandbox |
| M3 | Primeira regra de automação executando de ponta a ponta com histórico e simulação |
| M4 | Cofres + Pix simulado integrados ao motor |
| M5 | App Android com login, saldo, extrato e transferência |
