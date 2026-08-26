package ElFabrica.Wallet_pay.wallet.controllers;

import ElFabrica.Wallet_pay.wallet.dto.WalletBalanceResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.StatementEntryResponseDTO;
import ElFabrica.Wallet_pay.wallet.dto.StatementResponseDTO;
import ElFabrica.Wallet_pay.wallet.service.GetWalletBalanceUseCase;
import ElFabrica.Wallet_pay.wallet.service.GetWalletStatementUseCase;
import ElFabrica.Wallet_pay.wallet.service.StatementQuery;
import ElFabrica.Wallet_pay.wallet.service.StatementResult;
import ElFabrica.Wallet_pay.wallet.service.WalletBalanceResult;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final GetWalletBalanceUseCase getWalletBalanceUseCase;
    private final GetWalletStatementUseCase getWalletStatementUseCase;

    public WalletController(
            GetWalletBalanceUseCase getWalletBalanceUseCase,
            GetWalletStatementUseCase getWalletStatementUseCase
    ) {
        this.getWalletBalanceUseCase = getWalletBalanceUseCase;
        this.getWalletStatementUseCase = getWalletStatementUseCase;
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
}
