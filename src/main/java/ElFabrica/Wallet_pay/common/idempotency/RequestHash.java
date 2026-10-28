package ElFabrica.Wallet_pay.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

public final class RequestHash {

    private RequestHash() {
    }

    public static String of(String... fields) {
        String canonical = Arrays.stream(fields)
                .map(field -> field == null ? "" : field)
                .collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }
}
