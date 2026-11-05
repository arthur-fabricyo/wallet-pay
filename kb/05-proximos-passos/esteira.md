# Esteira de trabalho

Lista **única e ordenada** do que fazer a seguir: fatias da Fase 4, pendências das fases entregues e correções encontradas
no código. É a fonte de "o que fazer agora": puxe sempre o primeiro item ⬜ cujas dependências estejam ✅.

> **Status deste documento:** a ordem é uma **proposta** (2026-11-05) baseada no critério do dono do projeto —
> "o mais completo possível": base sólida e Fase 3 fechada antes do motor de automações. Pode ser reordenada.

Legenda de status: ✅ feito · 🟡 em andamento/parcial · ⬜ não iniciado · ❓ precisa de decisão do dono do projeto.
Tamanho: **R** ajuste rápido · **P** mudança pequena (descrição no PR) · **D** projeto com design doc + plano.
Referências `#n` apontam para [pendências e dívida técnica](../04-pendencias/pendencias-e-divida-tecnica.md); `Dn` para as
[decisões abertas](proximos-passos.md#decisões-abertas-precisam-do-dono-do-projeto).

## Como usar

1. Pegue o primeiro item ⬜ da lista cujas dependências estejam ✅. Itens ❓ exigem a decisão antes.
2. Tamanho **D**: design em `docs/superpowers/specs/AAAA-MM-DD-<tema>-design.md` e plano em `docs/superpowers/plans/`.
   Tamanho **P**/**R**: a descrição do PR basta.
3. Branch a partir de `dev` (`feat/…`, `fix/…`, `docs/…`), um commit por task, PR para `dev`.
4. Ao concluir: marque ✅ aqui, mova a pendência correspondente para resolvida e atualize
   [estado atual](../02-andamento/estado-atual.md), [linha do tempo](../02-andamento/linha-do-tempo.md) e
   [roadmap](../01-produto/roadmap.md).
5. Item novo descoberto no caminho: registre em pendências **e** insira aqui na posição certa.

## Bloco 0 — Arrumação

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| E00 | Atualizar a KB após a Fase 3 e criar esta esteira | KB parou na Fase 2 em vários pontos; achados da revisão não estavam registrados | — | R | ✅ |
| E01 | Rodar CI também em push para `dev` | Hoje `ci.yml` só roda em push para `main` e em PRs (#22) | — | R | ✅ |
| E02 | ❓ Promover `dev` → `main` (D1) | `main` ainda não tem nenhuma fase | E01 | R | ❓ |
| E03 | Marcar o plano `2026-10-15-fases-1-2.md` como concluído e refletir os ajustes do plano nas specs | Checkboxes todos `- [ ]`; specs divergem das decisões T17 etc. (#2, #4) | — | R | ⬜ |

## Bloco A — Base de eventos (pré-requisito do motor)

O motor vai reagir a eventos; um outbox travado ou um evento sem origem compromete tudo que vier depois.

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| A01 | Outbox resiliente: teto de tentativas, estado "estacionado" para linha venenosa, parse dentro do `try`, não segurar transação/locks durante o I/O do broker | Uma linha ruim bloqueia a fila inteira para sempre (#11e, #28) | — | P | ⬜ |
| A02 | DLQ consultável e reprocessável, com alerta (log/métrica) | Mensagens na DLQ hoje ficam invisíveis (#11b, #11e) | A01 | P | ⬜ |
| A03 | Retenção de `outbox_events` e `notification_deliveries` | Crescem sem limpeza (#11d) | A01 | R | ⬜ |
| A04 | Agendador com pool de threads e padrão de job seguro para várias instâncias | Pool padrão tem 1 thread: um job lento atrasa o publicador do outbox (#31) | — | P | ⬜ |
| A05 | Evento de depósito e evento de dinheiro recebido (`MoneyReceived`/`DepositCompleted`; avaliar `BalanceUpdated`) | Depósito não emite evento; "quando entrar dinheiro" não tem gatilho (backlog Fase 3) | A01 | P | ⬜ |
| A06 | Origem da operação: quem iniciou (usuário × automação) no evento e em `transactions` | Transferência feita por automação emite `TransferCompleted` idêntico ao manual → risco de cascata entre regras (#29) | A05 | P | ⬜ |
| A07 | Auditoria com ator `SYSTEM` (e ator explícito em jobs/consumidores) | `AuditActorType.SYSTEM` existe mas o `AuditLogger` só grava `USER`/`ANONYMOUS` (#30) | — | R | ⬜ |
| A08 | Métricas de eventos com Micrometer (publicados, falhas, atraso do outbox, DLQ) | "Métricas e logs" da Fase 3 não foram feitos (#11b, #23) | A01, A02 | P | ⬜ |
| A09 | Medir a duração da transação interna das operações de saldo | Risco registrado: cresce a cada checagem; medir antes da Fase 4 | A08 | R | ⬜ |

## Bloco B — Fechar a Fase 3

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| B01 | Notificação de depósito / dinheiro recebido | Primeiro uso do evento de A05 | A05 | P | ⬜ |
| B02 | Histórico de notificações (tabela + `GET /notifications`) | `core.md` §3.6; hoje só existe o controle de entrega (#11b) | — | D | ⬜ |
| B03 | Preferências por tipo de notificação e frequência | `core.md` §3.6 pede; hoje só por canal | B02 | P | ⬜ |
| B04 | Migrar verificação de e-mail e recuperação de senha para o `EmailSender` | Tokens ainda vão para o log (#11) | B05 | P | ⬜ |
| B05 | SMTP com timeouts, autenticação e TLS configuráveis | SMTP travado prende a thread do consumidor (#11f) | — | R | ⬜ |
| B06 | Templates finais de e-mail | Specs 04/05 (#12) | B04 | P | ⬜ |
| B07 | Correções menores da revisão da Fase 3: push stub sem dado pessoal em INFO; usuário desconhecido não derruba a notificação do outro lado; `displayName` remove U+0085/U+2028/U+2029; trocar `Thread.sleep` dos testes | Achados "minor" da revisão final (#11g, #32) | — | R | ⬜ |

## Bloco C — Higiene de código e decisões pendentes

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| C01 | ❓ Decidir T29 (Mockito) e T31 (acentos em textos) e alinhar código e KB | A Fase 3 usou `@MockitoSpyBean` e textos com acento, contrariando as decisões (#37) | — | R | ❓ |
| C02 | Relógio único: tirar `Instant.now()` de entidades e serviços de auth | Contraria T22; testes com `MutableClock` ficam inconsistentes (#33) | — | P | ⬜ |
| C03 | Enums × checks do banco: `WITHDRAW`/`REFUND`/`FEE`/`PENDING` | Valores que o banco rejeita (#14, #34) | — | R | ⬜ |
| C04 | Validações no use case (destinatário nulo, descrição > 255) e `@Valid` em `PUT /wallets/me/limits` | Chamadas internas (motor) não passam pelos DTOs (#35) | — | R | ⬜ |
| C05 | `ErrorCode.error()` aceitar 429 (e outros status) | Pré-requisito do rate limiting (#36) | — | R | ⬜ |
| C06 | Unificar prefixos de configuração `wallet-pay.*` × `walletpay.*` | Dois prefixos convivendo (#38) | — | R | ⬜ |

## Bloco D — Segurança antes de automações que movem dinheiro

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| D01 | Exigir `JWT_SECRET` fora do ambiente local | Segredo padrão no `application.properties` (#7) | — | R | ⬜ |
| D02 | ❓ Rate limiting e bloqueio por tentativas de login (D4) | Endpoints públicos sem proteção (#5, #6) | C05 | D | ❓ |
| D03 | Autorização reforçada para operações sensíveis (confirmação acima de um valor) | Base para a "confirmação do usuário" do motor (#9) | — | D | ⬜ |

## Bloco F4 — Fase 4: motor de automações (em fatias)

Escopo mínimo (D6) e categorias (D5) ainda **em discussão**; as fatias abaixo são a proposta atual.

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| F4-00 | ❓ Fechar D5 (categorias) e D6 (escopo mínimo); design doc e plano da Fase 4 | Define gatilhos, condições e ações da primeira versão | — | D | 🟡 |
| F4-01 | Modelo de regra e cadastro: criar, editar, pausar, retomar, excluir; validação; auditoria das alterações | `core.md` §2 | F4-00, A07 | D | ⬜ |
| F4-02 | Avaliador por evento ("dinheiro recebido") + ação "avisar" + histórico de execuções + idempotência por (regra, evento) | Primeira regra ponta a ponta (marco M3) | F4-01, A05, B01 | D | ⬜ |
| F4-03 | Ação "transferir" (valor fixo e percentual), revalidada antes de executar e pelo padrão de operações de saldo; proteção contra cascata | Ação que move dinheiro | F4-02, A06, C04 | D | ⬜ |
| F4-04 | Condições compostas (todas verdadeiras) sobre valor e saldo, inclusive "saldo após a operação" | Exemplos de `core.md` §2 | F4-03 | P | ⬜ |
| F4-05 | Agendamento e recorrência (ex.: todo dia 5), seguro com várias instâncias | `core.md` §8 | F4-03, A04 | D | ⬜ |
| F4-06 | Limites de valor e frequência por regra; confirmação do usuário quando exigida | `core.md` §2; specs 12/14 deixaram para a Fase 4 | F4-03, D03 | D | ⬜ |
| F4-07 | Simulação (dry-run) antes de ativar | `core.md` §2 | F4-04 | P | ⬜ |
| F4-08 | Eventos `AutomationTriggered/Executed/Failed` e notificações de execução, falha e pausa | `core.md` §3.6 e §7 | F4-02, B02 | P | ⬜ |
| F4-09 | Endpoints de histórico, testes ponta a ponta e atualização da KB | Fechamento da fase | F4-01..08 | P | ⬜ |

## Bloco G — Operação e observabilidade (Fase 9 antecipada)

| ID | Item | Por quê | Depende de | Tam. | Status |
|---|---|---|---|---|---|
| G01 | Dockerfile e serviço da aplicação no Compose | Compose sobe só a infraestrutura (#24) | — | P | ⬜ |
| G02 | Cobertura com JaCoCo no CI | Sem relatório (#25) | E01 | R | ⬜ |
| G03 | Endpoint de consulta da auditoria, com retenção | Hoje só por SQL (#20) | — | P | ⬜ |
| G04 | Prometheus + Grafana, dashboards e alertas | Fase 9 | A08 | D | ⬜ |
| G05 | Circuit breaker no cliente do CNPJ.ws | Só timeouts hoje (#27) | — | R | ⬜ |
| G06 | IP real atrás de proxy (`X-Forwarded-For` confiável) | Ao publicar atrás de gateway (#10) | — | R | ⬜ |
| G07 | Documentar/definir JDK 21 na máquina de dev | `JAVA_HOME` ausente (#26) | — | R | ⬜ |

## Bloco H — Fases seguintes e evoluções

Cada item vira um design próprio quando chegar a vez. Detalhe em [backlog por fase](../04-pendencias/backlog-por-fase.md).

| ID | Item | Depende de | Tam. | Status |
|---|---|---|---|---|
| H01 | Fase 5 — cofres e metas (destino de "reservar X%") e integração com o motor | F4-09 | D | ⬜ |
| H02 | Fase 5 — orçamentos por categoria, alertas 80%/100%, bloqueio opcional; despesas recorrentes | F4-00 (D5) | D | ⬜ |
| H03 | Fase 6 — Pix simulado, webhook idempotente, `PENDING` no banco | C03 | D | ⬜ |
| H04 | Estorno e saque (#13); transações `FAILED` no extrato? (#15) | C03 | D | ⬜ |
| H05 | Comprovante em PDF (#16) e extrato em CSV/PDF (#17) | — | P | ⬜ |
| H06 | Aumento de limite com carência; limites por destinatário/categoria (#19) | — | P | ⬜ |
| H07 | Idempotência em cadastro/login/recuperação (#21); 2FA (#8) | — | P | ⬜ |
| H08 | Várias carteiras / moedas (#18) | — | D | ⬜ |
| H09 | Fase 7 — app Android | F4-09 | D | ⬜ |
| H10 | Fase 8 — integração bancária experimental | — | D | ⬜ |
| H11 | Fase 10 — assistente, modelos de automação, carteiras compartilhadas, cartões | F4-09 | D | ⬜ |
