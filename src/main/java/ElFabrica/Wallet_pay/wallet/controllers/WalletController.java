package ElFabrica.Wallet_pay.wallet.controllers;

import ElFabrica.Wallet_pay.common.idempotency.IdempotencyKeys;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.wallet.dto.DepositRequestDTO;
import ElFabrica.Wallet_pay.wallet.dto.DepositResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.DepositWalletResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.StatementEntryResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.StatementResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.UpdateWalletLimitsRequestDTO;
import ElFabrica.Wallet_pay.wallet.dto.WalletBalanceResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.WalletLimitsResponseDTO;
import ElFabrica.Wallet_pay.wallet.service.DepositCommand;
import ElFabrica.Wallet_pay.wallet.service.DepositResult;
import ElFabrica.Wallet_pay.wallet.service.DepositService;
import ElFabrica.Wallet_pay.wallet.service.GetWalletBalanceUseCase;
import ElFabrica.Wallet_pay.wallet.service.GetWalletLimitsUseCase;
import ElFabrica.Wallet_pay.wallet.service.GetWalletStatementUseCase;
import ElFabrica.Wallet_pay.wallet.service.StatementQuery;
import ElFabrica.Wallet_pay.wallet.service.StatementResult;
import ElFabrica.Wallet_pay.wallet.service.UpdateWalletLimitsCommand;
import ElFabrica.Wallet_pay.wallet.service.UpdateWalletLimitsUseCase;
import ElFabrica.Wallet_pay.wallet.service.WalletBalanceResult;
import ElFabrica.Wallet_pay.wallet.service.WalletLimitsResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final GetWalletBalanceUseCase getWalletBalanceUseCase;
    private final GetWalletStatementUseCase getWalletStatementUseCase;
    private final DepositService depositService;
    private final GetWalletLimitsUseCase getWalletLimitsUseCase;
    private final UpdateWalletLimitsUseCase updateWalletLimitsUseCase;

    public WalletController(
            GetWalletBalanceUseCase getWalletBalanceUseCase,
            GetWalletStatementUseCase getWalletStatementUseCase,
            DepositService depositService,
            GetWalletLimitsUseCase getWalletLimitsUseCase,
            UpdateWalletLimitsUseCase updateWalletLimitsUseCase
    ) {
        this.getWalletBalanceUseCase = getWalletBalanceUseCase;
        this.getWalletStatementUseCase = getWalletStatementUseCase;
        this.depositService = depositService;
        this.getWalletLimitsUseCase = getWalletLimitsUseCase;
        this.updateWalletLimitsUseCase = updateWalletLimitsUseCase;
    }

    @GetMapping("/me/balance")
    ResponseEntity<WalletBalanceResponseDTO> getMyBalance(@AuthenticationPrincipal Jwt jwt) {
        WalletBalanceResult result = getWalletBalanceUseCase.getBalance(UUID.fromString(jwt.getSubject()));

        return ResponseEntity.ok(new WalletBalanceResponseDTO(
                result.walletId(),
                result.balance(),
                result.currency(),
                result.updatedAt()
        ));
    }

    @GetMapping("/me/statement")
    ResponseEntity<StatementResponseDTO> getMyStatement(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        StatementResult result = getWalletStatementUseCase.getStatement(
                UUID.fromString(jwt.getSubject()),
                new StatementQuery(page, size, from, to, type, sort)
        );

        return ResponseEntity.ok(new StatementResponseDTO(
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.items().stream()
                        .map(item -> new StatementEntryResponseDTO(
                                item.transactionId(),
                                item.type(),
                                item.status(),
                                item.direction(),
                                item.amount(),
                                item.currency(),
                                item.counterpartyName(),
                                item.description(),
                                item.createdAt(),
                                item.completedAt()
                        ))
                        .toList()
        ));
    }

    @PostMapping("/me/deposits")
    ResponseEntity<DepositResponseDTO> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody DepositRequestDTO request
    ) {
        IdempotentResult<DepositResult> outcome = depositService.deposit(new DepositCommand(
                UUID.fromString(jwt.getSubject()),
                request.amount(),
                request.description(),
                IdempotencyKeys.require(idempotencyKey)
        ));
        DepositResult result = outcome.result();

        ResponseEntity.BodyBuilder builder = ResponseEntity.created(URI.create("/transactions/" + result.transactionId()));
        if (outcome.replayed()) {
            builder.header(IdempotencyKeys.REPLAYED_HEADER, "true");
        }
        return builder.body(toResponse(result));
    }

    @GetMapping("/me/limits")
    ResponseEntity<WalletLimitsResponseDTO> getMyLimits(@AuthenticationPrincipal Jwt jwt) {
        WalletLimitsResult result = getWalletLimitsUseCase.getLimits(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(toResponse(result));
    }

    @PutMapping("/me/limits")
    ResponseEntity<WalletLimitsResponseDTO> updateMyLimits(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateWalletLimitsRequestDTO request
    ) {
        WalletLimitsResult result = updateWalletLimitsUseCase.update(new UpdateWalletLimitsCommand(
                UUID.fromString(jwt.getSubject()),
                request.transferPerOperation(),
                request.transferDaily(),
                request.transferMonthly(),
                request.transferNightPerOperation(),
                request.transferNightWindow()
        ));
        return ResponseEntity.ok(toResponse(result));
    }

    private static WalletLimitsResponseDTO toResponse(WalletLimitsResult result) {
        return new WalletLimitsResponseDTO(
                new WalletLimitsResponseDTO.TransferLimits(
                        limitValue(result.transfer().perOperation()),
                        periodValue(result.transfer().daily()),
                        periodValue(result.transfer().monthly()),
                        limitValue(result.transfer().nightPerOperation()),
                        periodValue(result.transfer().nightWindow())
                ),
                new WalletLimitsResponseDTO.DepositLimits(
                        limitValue(result.deposit().perOperation()),
                        periodValue(result.deposit().daily())
                ),
                new WalletLimitsResponseDTO.Night(
                        result.night().start(),
                        result.night().end(),
                        result.night().timezone(),
                        result.night().active()
                )
        );
    }

    private static WalletLimitsResponseDTO.LimitValue limitValue(WalletLimitsResult.LimitValue value) {
        return new WalletLimitsResponseDTO.LimitValue(value.effective(), value.global(), value.custom());
    }

    private static WalletLimitsResponseDTO.PeriodLimitValue periodValue(WalletLimitsResult.PeriodLimitValue value) {
        return new WalletLimitsResponseDTO.PeriodLimitValue(
                value.effective(),
                value.global(),
                value.custom(),
                value.used(),
                value.available()
        );
    }

    private static DepositResponseDTO toResponse(DepositResult result) {
        return new DepositResponseDTO(
                result.transactionId(),
                result.type(),
                result.status(),
                result.amount(),
                result.currency(),
                result.description(),
                new DepositWalletResponseDTO(
                        result.wallet().id(),
                        result.wallet().balance(),
                        result.wallet().currency(),
                        result.wallet().updatedAt()
                ),
                result.createdAt(),
                result.completedAt()
        );
    }
}
