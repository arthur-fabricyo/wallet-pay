# Fase 1 — Núcleo da carteira ✅

Specs: 01 a 09 em [`docs/specs`](../../docs/specs). Período: 2026-07-31 a 2026-08-26.

## Entregue

| Spec | Funcionalidade | Resumo | Código principal |
|---|---|---|---|
| [01](../../docs/specs/01-user.md) | Cadastro de usuário | Nome, e-mail, senha (hash), `document` único (CPF ou CNPJ). Valida dígitos do CPF; CNPJ consultado no CNPJ.ws. Cria a carteira BRL automaticamente. `emailVerified=false` ao nascer | `user/` (`RegisterUserUseCase`, `CpfValidator`, `CnpjWsValidatorGateway`) |
| [02](../../docs/specs/02-auth.md) | Autenticação | Login por e-mail/senha; access token JWT de 20 min; refresh token de 7 dias; logout; bloqueia e-mail não verificado | `auth/` (`AuthService`, `AccessTokenService`, `LoginUseCase`) |
| [03](../../docs/specs/03-wallet.md) | Carteira e saldo | `GET /wallets/me/balance`; saldo como string com 2 casas | `wallet/` (`GetWalletBalanceUseCase`) |
| [04](../../docs/specs/04-email-verification.md) | Verificação de e-mail | Token na criação, confirmação, expiração, reenvio. Envio simulado em log | `auth/` (`EmailVerificationService`) |
| [05](../../docs/specs/05-password-recovery.md) | Recuperação de senha | Token temporário, uso único, invalida após uso | `auth/` (`PasswordRecoveryService`) |
| [06](../../docs/specs/06-transfer.md) | Transferência | Débito/crédito atômicos, validação de saldo, transação `COMPLETED` | `transaction/` (`CreateTransferUseCase`) |
| [07](../../docs/specs/07-receipt.md) | Comprovante | Derivado da transação, JSON, só participantes acessam, documentos mascarados | `transaction/` (`GetReceiptUseCase`) |
| [08](../../docs/specs/08-statement.md) | Extrato | Paginado, filtro por período e tipo, ordenação por data, direção `IN`/`OUT` | `wallet/` (`GetWalletStatementUseCase`) |
| [09](../../docs/specs/09-controller-contract-organization.md) | Organização dos contratos HTTP | Pasta `controllers` só com controllers; DTOs em `dto/` com sufixo `DTO` | todos os módulos |

## Regras de domínio que valem desde esta fase

- O documento único do usuário (CPF/CNPJ, e RG quando aplicável) é sempre tratado como `document` ([`docs.md`](../../docs.md)).
- Valores monetários trafegam como **string** com duas casas decimais.
- Toda carteira pertence a exatamente um usuário.
- Mensagens de erro e textos de domínio em português sem acento (padrão do código).

## O que ficou de fora de propósito

Declarado nas próprias specs como fora de escopo e **ainda não existente**: 2FA, device fingerprint, bloqueio por tentativas
de login, templates finais de e-mail, provedor real de e-mail, PDF/assinatura do comprovante, exportação CSV/PDF do extrato,
cache de saldo/extrato em Redis, estorno, transferência externa para bancos.
Ver [pendências](../04-pendencias/pendencias-e-divida-tecnica.md).

## Lacuna conhecida

O roadmap lista "testes de regras financeiras e **concorrência**" na Fase 1, mas a proteção e os testes de concorrência
só chegaram na Fase 2 (spec 10). Hoje isso está coberto — ver [operações de saldo](../03-arquitetura/operacoes-de-saldo.md).
