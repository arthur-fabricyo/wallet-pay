# OutPay --- Carteira Digital Programável

## 1. Visão do projeto

O **OutPay** é uma proposta de carteira digital com foco em automações
financeiras configuráveis pelo usuário. Em vez de ser apenas mais uma
carteira para consultar saldo e fazer transferências, a ideia é permitir
que o usuário defina regras para movimentar, organizar e acompanhar seu
dinheiro.

O projeto será desenvolvido como portfólio, priorizando uma API backend
com **Java e Spring Boot** e, posteriormente, um aplicativo Android com
**Kotlin e Jetpack Compose**.

> **Nota de escopo:** integrações com bancos reais, investimentos reais
> e iniciação de pagamentos dependem de provedores, autorizações,
> requisitos regulatórios e contratos apropriados. O MVP deve usar
> simulações ou sandbox.

## 2. Diferencial principal: motor de automações financeiras

O usuário configura regras no formato **"quando acontecer X, faça Y"**.

### Exemplos de regras

-   **Saldo acima de um limite:** quando o saldo ultrapassar R\$ 5.000,
    separar R\$ 1.000 para uma reserva.
-   **Transferência condicionada:** enviar R\$ 200 para um destinatário
    apenas se o saldo permanecer acima de R\$ 2.000 após a operação.
-   **Separação de recebimentos:** quando entrar um valor identificado
    como salário, destinar uma porcentagem a uma meta.
-   **Transferência recorrente:** enviar um valor definido em uma data
    mensal.
-   **Alerta de gastos:** avisar quando as despesas de uma categoria
    ultrapassarem o limite configurado.
-   **Regra percentual:** reservar 15% de uma entrada elegível.
-   **Regra com múltiplas condições:** executar uma ação apenas se todas
    as condições configuradas forem verdadeiras.

### Capacidades do motor

-   Criar, editar, pausar, retomar e excluir regras.
-   Definir gatilhos, condições, ações, frequência e limites.
-   Validar regras antes de ativá-las.
-   Mostrar histórico de execuções, resultados e falhas.
-   Evitar execução duplicada da mesma regra para o mesmo evento.
-   Aplicar limites de valor e frequência, com opção de exigir
    confirmação do usuário.
-   Permitir simular uma regra antes de ativá-la.
-   Registrar auditoria das alterações e execuções.

## 3. Funcionalidades financeiras

### 3.1 Carteira e contas

-   Cadastro e autenticação de usuários.
-   Uma ou mais carteiras por usuário, conforme a evolução do projeto.
-   Consulta de saldo disponível e valores reservados.
-   Extrato paginado e detalhado.
-   Histórico de entradas, saídas, transferências e ajustes.
-   Suporte futuro a múltiplas moedas, com tratamento explícito de
    conversão e taxas.

### 3.2 Transferências

-   Transferências entre usuários OutPay.
-   Validação de saldo e limites.
-   Registro consistente de débito e crédito.
-   Idempotência para impedir duplicidade em tentativas repetidas.
-   Comprovante e status da operação.
-   Agendamento e recorrência.
-   Transferências condicionais, sujeitas a confirmação e às regras de
    segurança.

### 3.3 Cofres e metas financeiras

-   Criar metas como viagem, reserva de emergência ou compra planejada.
-   Definir valor-alvo e prazo.
-   Acompanhar progresso e aportes.
-   Reservar parte do saldo para uma meta.
-   Integrar cofres ao motor de automações.
-   Calcular uma sugestão de contribuição periódica com base no prazo e
    no valor restante.

### 3.4 Orçamentos e limites de gastos

-   Definir orçamento mensal por categoria.
-   Emitir alertas ao atingir percentuais configuráveis, como 80% e
    100%.
-   Oferecer bloqueio opcional de ações dentro do próprio OutPay quando
    um limite for atingido.
-   Comparar gastos do mês com períodos anteriores.
-   Exibir previsões simples com base no ritmo de gastos.

### 3.5 Pix e pagamentos

-   Simular criação de cobranças Pix e geração de QR Code.
-   Receber confirmação por webhook simulado.
-   Atualizar o estado da transação com segurança.
-   Tratar repetição de webhooks e eventos fora de ordem.
-   Evoluir para integração com um provedor de pagamentos, em ambiente
    de testes, antes de considerar operações reais.
-   Explorar pagamentos agendados ou recorrentes somente quando houver
    suporte explícito do provedor e autorização aplicável.

### 3.6 Notificações e alertas

-   Notificações de transferências recebidas e realizadas.
-   Avisos sobre execução, falha ou pausa de automações.
-   Alertas de orçamento e saldo.
-   Preferências de canal e frequência.
-   Histórico de notificações.

### 3.7 Assinaturas e despesas recorrentes

-   Cadastro manual de despesas recorrentes.
-   Estimativa de gastos fixos mensais.
-   Alertas de vencimento.
-   Comparação de valores recorrentes para identificar possíveis
    aumentos.
-   Detecção automática de recorrências como evolução futura, dependendo
    da qualidade dos dados disponíveis.

### 3.8 Carteiras compartilhadas e permissões

-   Compartilhar uma carteira ou meta com outras pessoas.
-   Definir permissões para visualizar, contribuir ou movimentar
    valores.
-   Fluxos de aprovação para ações sensíveis.
-   Histórico de ações por membro.
-   Casos de uso como despesas domésticas e metas compartilhadas.

### 3.9 Cartões virtuais inteligentes

Funcionalidade futura, dependente de parceiro emissor ou infraestrutura
compatível.

-   Criar cartões virtuais por finalidade.
-   Definir limites por cartão e período.
-   Pausar ou cancelar cartões.
-   Exibir transações e alertas.
-   Aplicar restrições por categoria ou contexto apenas se a
    infraestrutura de emissão suportar essas regras.

### 3.10 Assistente financeiro

-   Resumir entradas e despesas por período.
-   Apontar variações relevantes de gastos.
-   Responder perguntas sobre os dados disponíveis na carteira.
-   Sugerir ajustes de orçamento com explicação transparente.
-   Não executar movimentações financeiras sem autorização explícita.
-   Tratar recomendações como informativas, sem prometer rentabilidade
    ou resultados financeiros.

### 3.11 Pontuação e incentivos financeiros

Possível experimento futuro de educação financeira, sem confundir com
score de crédito oficial.

-   Indicadores de consistência no orçamento.
-   Progresso das metas.
-   Regularidade de aportes.
-   Recomendações para melhorar hábitos financeiros.
-   Critérios explicáveis, sem usar a pontuação como decisão de crédito.

### 3.12 Modelos de automação

-   Galeria de modelos prontos, como "guardar uma porcentagem das
    entradas".
-   Duplicar e personalizar modelos.
-   Compartilhar modelos sem expor saldos ou dados pessoais.
-   Validar condições e riscos antes de ativar um modelo.

## 4. Integração com bancos e Open Finance

### Estratégia recomendada

Para o portfólio, não é necessário construir uma integração independente
com cada banco. A evolução mais prática é criar uma camada de integração
desacoplada e, quando necessário, conectar um provedor de Open Finance
ou de pagamentos que ofereça os serviços desejados.

A disponibilidade de dados e operações varia por provedor, instituição,
consentimento e produto contratado.

### Camada de integração

O restante do OutPay deve conversar com interfaces internas estáveis,
sem depender diretamente do formato de uma instituição específica.

Exemplo conceitual:

``` text
OutPay
  └── Banking Integration
       ├── BankingProvider (interface)
       ├── ProviderAdapterA
       ├── ProviderAdapterB
       └── SandboxBankingProvider
```

Responsabilidades dessa camada:

-   Padronizar respostas de saldo, contas e transações.
-   Isolar autenticação, tokens e particularidades de cada provedor.
-   Gerenciar consentimentos e sua validade.
-   Tratar limites de requisição, indisponibilidade, tentativas e
    timeouts.
-   Normalizar erros e estados.
-   Registrar logs e métricas sem expor credenciais ou dados sensíveis.
-   Implementar webhooks com validação, idempotência e rastreabilidade.

### Integração direta banco a banco

Uma integração própria para cada instituição pode ser justificada em
empresas de maior escala ou quando há requisitos específicos, mas exige
avaliação individual de:

-   Existência e acesso autorizado às APIs.
-   Contratos, credenciamento e requisitos regulatórios.
-   Padrões de autenticação e segurança.
-   Escopos e consentimentos permitidos.
-   Diferenças de recursos e formatos.
-   Monitoramento e manutenção contínua das integrações.

Não se deve presumir que qualquer banco ofereça uma API pública para
consultar contas ou iniciar transferências livremente.

### Limites do portfólio

-   **MVP:** simular instituições e operações bancárias.
-   **Evolução:** conectar a um sandbox ou provedor real, se houver
    acesso.
-   **Produção:** realizar avaliação jurídica, de segurança,
    privacidade, contratos e requisitos regulatórios antes de movimentar
    dinheiro real.

## 5. Segurança e confiabilidade

-   Autenticação e autorização por usuário.
-   Controle de acesso a carteiras e recursos.
-   Autorização reforçada para operações sensíveis.
-   Limites por operação e por período.
-   Idempotência em transferências, webhooks e execuções de regras.
-   Transações de banco de dados consistentes.
-   Testes de concorrência para evitar saldo incorreto.
-   Auditoria de alterações e movimentações.
-   Proteção de segredos e credenciais.
-   Rate limiting e proteção contra tentativas excessivas.
-   Validação de assinatura/autenticidade de webhooks quando aplicável.
-   Política de retentativas e fila de mensagens não processadas.
-   Nunca armazenar senhas bancárias de usuários como forma de
    integração.

## 6. Arquitetura técnica sugerida

### Backend

-   Java.
-   Spring Boot.
-   Spring Security.
-   Spring Data JPA.
-   PostgreSQL.
-   RabbitMQ para processamento assíncrono e eventos.
-   Redis para casos de uso de cache, controle temporário e coordenação,
    quando adequado.
-   OpenAPI/Swagger para documentação.
-   Testes unitários e de integração.

### Aplicativo

-   Kotlin.
-   Jetpack Compose.
-   ViewModel.
-   Coroutines.
-   Retrofit ou cliente HTTP equivalente.

### Infraestrutura e observabilidade

-   Docker e Docker Compose.
-   CI/CD.
-   Micrometer.
-   Prometheus e Grafana.
-   Logs estruturados e rastreabilidade de operações.

### Organização conceitual dos módulos

``` text
outpay
├── auth
├── user
├── wallet
├── account
├── transaction
├── automation
├── budget
├── goal
├── payment
├── bankingintegration
├── notification
├── webhook
├── audit
└── infrastructure
```

A estrutura é uma proposta inicial. O projeto pode começar como um
monólito modular e separar serviços somente quando houver uma
necessidade real.

## 7. Eventos e processamento assíncrono

Exemplos de eventos de domínio:

-   `MoneyReceived`
-   `TransferCompleted`
-   `BalanceUpdated`
-   `BudgetLimitReached`
-   `AutomationTriggered`
-   `AutomationExecuted`
-   `AutomationFailed`
-   `PaymentConfirmed`

Fluxo conceitual:

1.  Uma operação financeira é validada e persistida.
2.  O sistema registra o evento de forma confiável.
3.  Um consumidor processa o evento.
4.  O motor avalia as regras ativas.
5.  A ação é validada novamente antes da execução.
6.  O resultado é registrado e o usuário é notificado.

Para operações financeiras, mensageria não substitui transações,
idempotência ou validação de saldo. Uma abordagem como **Transactional
Outbox** pode ajudar a manter a consistência entre a gravação no banco e
a publicação de eventos.

## 8. Roadmap sugerido

### Fase 1 --- Núcleo da carteira

-   Cadastro e autenticação.
-   Carteira e saldo.
-   Transferências internas.
-   Extrato paginado.
-   Testes de regras financeiras e concorrência.

### Fase 2 --- Segurança e confiabilidade

-   Idempotência.
-   Auditoria.
-   Limites de operação.
-   Tratamento de falhas.
-   Testes de integração.

### Fase 3 --- Eventos e notificações

-   RabbitMQ.
-   Eventos de domínio.
-   Notificações assíncronas.
-   Retentativas e fila de mensagens não processadas.
-   Métricas e logs.

### Fase 4 --- Motor de automações

-   Cadastro de regras.
-   Gatilhos, condições e ações.
-   Agendamento.
-   Histórico de execução.
-   Proteções contra duplicidade e execução indevida.
-   Simulação de regras antes da ativação.

### Fase 5 --- Organização financeira

-   Cofres e metas.
-   Orçamentos por categoria.
-   Alertas de limite.
-   Despesas recorrentes.

### Fase 6 --- Pagamentos simulados

-   Cobrança Pix simulada.
-   QR Code.
-   Webhook.
-   Confirmação idempotente.
-   Atualização do saldo e publicação de eventos.

### Fase 7 --- Aplicativo Android

-   Login e cadastro.
-   Home e saldos.
-   Extrato.
-   Transferências.
-   Cofres e metas.
-   Criação e acompanhamento de automações.

### Fase 8 --- Integração bancária experimental

-   Definir o caso de uso: leitura de dados, iniciação de pagamento ou
    outro recurso.
-   Selecionar provedor compatível.
-   Usar sandbox e consentimentos de teste.
-   Implementar adaptador e normalização.
-   Validar falhas, revogação de consentimento e expiração de tokens.

### Fase 9 --- Infraestrutura e observabilidade

-   Docker Compose.
-   CI/CD.
-   Métricas de operações e automações.
-   Dashboards.
-   Alertas e rastreamento de erros.

### Fase 10 --- Funcionalidades avançadas

-   Assistente financeiro.
-   Modelos compartilháveis de automação.
-   Carteiras compartilhadas.
-   Cartões virtuais, se houver parceiro compatível.
-   Open Finance, de acordo com viabilidade técnica e regulatória.

## 9. Prioridades do produto

**Diferencial central:** motor de automações financeiras.

**Base obrigatória:** carteira, transferências consistentes, extrato,
segurança, idempotência e auditoria.

**Próximas entregas:** cofres, orçamento, notificações e Pix simulado.

**Evoluções posteriores:** Open Finance, cartões, assistente com IA e
recursos compartilhados.

O objetivo é construir um projeto demonstrável e tecnicamente sólido,
sem depender de acesso bancário real para validar o núcleo do produto.
