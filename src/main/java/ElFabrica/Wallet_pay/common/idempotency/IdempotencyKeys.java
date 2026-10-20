package ElFabrica.Wallet_pay.common.idempotency;

import java.util.regex.Pattern;

public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    private IdempotencyKeys() {
    }

    public static String require(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key e obrigatorio");
        }
        if (!VALID_KEY.matcher(rawKey).matches()) {
            throw new IdempotencyKeyInvalidException(
                    "Idempotency-Key deve ter de 1 a 100 caracteres entre A-Z, a-z, 0-9, _ e -"
            );
        }
        return rawKey;
    }
}
