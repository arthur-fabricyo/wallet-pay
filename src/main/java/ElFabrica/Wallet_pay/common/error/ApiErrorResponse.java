package ElFabrica.Wallet_pay.common.error;

import java.util.Map;

public record ApiErrorResponse(
        int status,
        String error,
        String code,
        String message,
        Map<String, String> fields
) {
    public static ApiErrorResponse of(ErrorCode code, String message, Map<String, String> fields) {
        return new ApiErrorResponse(
                code.status(),
                code.error(),
                code.name(),
                message,
                fields == null ? Map.of() : fields
        );
    }
}
