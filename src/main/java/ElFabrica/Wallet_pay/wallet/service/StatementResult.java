package ElFabrica.Wallet_pay.wallet.service;

import java.util.List;

public record StatementResult(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<StatementEntryResult> items
) {
}
