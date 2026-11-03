# Visão do produto

Fonte completa: [`docs/core.md`](../../docs/core.md). Aqui fica o resumo.

## O que é o OutPay

Uma carteira digital **programável**. Além de saldo e transferências, o usuário define regras
("quando X acontecer, faça Y") para movimentar, organizar e acompanhar o próprio dinheiro.

Projeto de **portfólio**: API backend em Java/Spring Boot primeiro; aplicativo Android (Kotlin + Jetpack Compose) depois.

> **Escopo:** integrações com bancos, investimentos e iniciação de pagamentos reais dependem de provedores,
> autorizações e requisitos regulatórios. O MVP usa **simulação/sandbox**.

## Diferencial: motor de automações

Exemplos de regras previstas:

- Saldo acima de um limite → separar valor para uma reserva.
- Transferência condicionada ao saldo após a operação.
- Percentual de uma entrada identificada (ex.: salário) vai para uma meta.
- Transferência recorrente em data mensal.
- Alerta quando gastos de uma categoria passam de um limite.
- Regras com múltiplas condições.

Capacidades: criar/editar/pausar/excluir regras; gatilhos, condições, ações e limites; validar e **simular** antes de
ativar; histórico de execuções; evitar execução duplicada; confirmação do usuário; auditoria.

## Pilares

1. **Base obrigatória:** carteira, transferências consistentes, extrato, segurança, idempotência e auditoria. *(feito — Fases 1 e 2)*
2. **Diferencial central:** motor de automações. *(Fase 4)*
3. **Próximas entregas de produto:** cofres, orçamento, notificações e Pix simulado.
4. **Evoluções posteriores:** Open Finance, cartões virtuais, assistente com IA, carteiras compartilhadas.

## Princípios que guiam as decisões

- Mensageria **não substitui** transação, idempotência nem validação de saldo (usar *Transactional Outbox* para eventos).
- Começar como **monólito modular**; separar serviços só com necessidade real.
- Integração bancária sempre atrás de uma interface (`BankingProvider`) com um adaptador sandbox.
- Nunca guardar senhas bancárias do usuário.
- Recomendações financeiras do assistente são informativas, sem prometer rentabilidade.

## Módulos conceituais (proposta de `core.md`)

`auth`, `user`, `wallet`, `account`, `transaction`, `automation`, `budget`, `goal`, `payment`,
`bankingintegration`, `notification`, `webhook`, `audit`, `infrastructure`.
Os módulos em **negrito** na [visão geral de arquitetura](../03-arquitetura/visao-geral.md) já existem no código.
