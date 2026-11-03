package ElFabrica.Wallet_pay.transaction.domain;

public final class TransactionDescription {

    private TransactionDescription() {
    }

    public static String normalize(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
