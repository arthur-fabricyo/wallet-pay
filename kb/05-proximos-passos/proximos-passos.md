# Próximos passos — para onde vamos

> **Status deste documento:** proposta (recomendação). A ordem abaixo é uma sugestão baseada no roadmap
> e nas dependências técnicas; **nada daqui foi decidido ou aprovado**. Decisões abertas estão no final.

## Sequência recomendada

### Passo 0 — Fechar a Fase 2 (curto)

1. Rodar `./mvnw test` com Docker ligado e confirmar a suíte verde.
2. Abrir PR de `feat/fases-1-2` → `main` (inclui as specs 10–15 e o plano) e mergear.
3. Marcar o plano como concluído; atualizar esta KB.
4. Decidir quais pendências 🔴 de [pendências](../04-pendencias/pendencias-e-divida-tecnica.md) entram antes da Fase 3
   (sugestão: CI com a suíte e falhar na subida sem `JWT_SECRET` fora de dev).

### Passo 1 — Fase 3: eventos e notificações

Por que agora: o motor de automações (Fase 4, o diferencial) **só faz sentido sobre eventos confiáveis**, e as notificações
são a primeira entrega visível para o usuário. Escopo e riscos em [fase-3-eventos-e-notificacoes.md](fase-3-eventos-e-notificacoes.md).

Fluxo de trabalho sugerido, igual ao da Fase 2: escrever as specs (16+), escrever um plano de tasks, implementar task a task com
um commit por task.

### Passo 2 — Fase 4: motor de automações

Entrar com um recorte mínimo e demonstrável: **uma regra** ("quando entrar dinheiro, avisar" e depois "reservar X%") com histórico de execução,
idempotência por (regra, evento) e simulação. Ampliar gatilhos/ações depois.

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
| D1 | Mergear Fase 2 já ou agrupar com a Fase 3? | PR único agora (recomendado: histórico limpo) × continuar na mesma branch | Agora |
| D2 | Broker da Fase 3 | RabbitMQ (previsto em `core.md`) × eventos internos do Spring primeiro, broker depois | Início da Fase 3 |
| D3 | Provedor de e-mail | SMTP/MailHog em dev + provedor transacional em prod × manter em log até a Fase 3 estar pronta | Fase 3 |
| D4 | Onde fica o rate limiting | Filtro em memória (Bucket4j) × Redis (já previsto para coordenação) × no gateway | Antes de expor a API |
| D5 | Modelo de categorias de transação | Necessário para orçamentos (Fase 5) e condições do motor (Fase 4) | Início da Fase 4 |
| D6 | Escopo mínimo do motor de automações (MVP) | Quais gatilhos/ações entram na primeira versão | Antes da Fase 4 |
| D7 | Estratégia de testes com broker | Testcontainers RabbitMQ × fakes em memória | Fase 3 |

## Marcos sugeridos

| Marco | Entrega demonstrável |
|---|---|
| M1 | Fase 2 em `main`, CI verde |
| M2 | Transferência/depósito emitem evento via outbox; notificação de "dinheiro recebido" chega por e-mail real/sandbox |
| M3 | Primeira regra de automação executando de ponta a ponta com histórico e simulação |
| M4 | Cofres + Pix simulado integrados ao motor |
| M5 | App Android com login, saldo, extrato e transferência |
