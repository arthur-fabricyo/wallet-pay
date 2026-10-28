package ElFabrica.Wallet_pay.common.idempotency;

public record IdempotentResult<T>(
        T result,
        boolean replayed
) {
}
