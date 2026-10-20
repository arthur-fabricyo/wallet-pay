package ElFabrica.Wallet_pay.wallet.controllers;

import ElFabrica.Wallet_pay.wallet.dto.DepositRequestDTO;
import ElFabrica.Wallet_pay.wallet.dto.DepositResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.DepositWalletResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.StatementEntryResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.StatementResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.WalletBalanceResponseDTO;
import ElFabrica.Wallet_pay.wallet.service.DepositCommand;
import ElFabrica.Wallet_pay.wallet.service.DepositResult;
import ElFabrica.Wallet_pay.wallet.service.DepositService;
import ElFabrica.Wallet_pay.wallet.service.GetWalletBalanceUseCase;
import ElFabrica.Wallet_pay.wallet.service.GetWalletStatementUseCase;
import ElFabrica.Wallet_pay.wallet.service.StatementQuery;
import ElFabrica.Wallet_pay.wallet.service.StatementResult;
import ElFabrica.Wallet_pay.wallet.service.WalletBalanceResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final GetWalletBalanceUseCase getWalletBalanceUseCase;
    private final GetWalletStatementUseCase getWalletStatementUseCase;
    private final DepositService depositService;

    public WalletController(
            GetWalletBalanceUseCase getWalletBalanceUseCase,
            GetWalletStatementUseCase getWalletStatementUseCase,
            DepositService depositService
    ) {
        this.getWalletBalanceUseCase = getWalletBalanceUseCase;
        this.getWalletStatementUseCase = getWalletStatementUseCase;
        this.depositService = depositService;
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
            @Valid @RequestBody DepositRequestDTO request
    ) {
        DepositResult result = depositService.deposit(new DepositCommand(
                UUID.fromString(jwt.getSubject()),
                request.amount(),
                request.description()
        ));

        return ResponseEntity
                .created(URI.create("/transactions/" + result.transactionId()))
                .body(toResponse(result));
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
