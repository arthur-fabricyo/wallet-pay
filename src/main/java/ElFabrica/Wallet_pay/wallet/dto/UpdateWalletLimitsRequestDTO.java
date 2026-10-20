package ElFabrica.Wallet_pay.wallet.dto;

public record UpdateWalletLimitsRequestDTO(
        String transferPerOperation,
        String transferDaily,
        String transferMonthly,
        String transferNightPerOperation,
        String transferNightWindow
) {
}
