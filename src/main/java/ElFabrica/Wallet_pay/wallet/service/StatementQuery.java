package ElFabrica.Wallet_pay.wallet.service;

public record StatementQuery(
        int page,
        int size,
        String from,
        String to,
        String type,
        String sort
) {
}
