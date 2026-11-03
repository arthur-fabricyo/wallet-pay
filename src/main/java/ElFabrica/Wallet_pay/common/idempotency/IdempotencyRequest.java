package ElFabrica.Wallet_pay.common.idempotency;

import java.util.UUID;

public record IdempotencyRequest(
        UUID userId,
        IdempotentOperation operation,
        String key,
        String requestHash
) {
}
