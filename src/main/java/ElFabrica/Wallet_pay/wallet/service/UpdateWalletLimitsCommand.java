package ElFabrica.Wallet_pay.wallet.service;

import java.util.UUID;

public record UpdateWalletLimitsCommand(
        UUID userId,
        String transferPerOperation,
        String transferDaily,
        String transferMonthly,
        String transferNightPerOperation,
        String transferNightWindow
) {
}
