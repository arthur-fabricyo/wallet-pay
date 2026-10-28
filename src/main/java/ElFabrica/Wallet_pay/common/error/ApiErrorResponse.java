package ElFabrica.Wallet_pay.common.error;

import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import java.util.Map;
import org.slf4j.MDC;

public record ApiErrorResponse(
        int status,
        String error,
        String code,
        String message,
        Map<String, String> fields,
        String requestId
) {
    public static ApiErrorResponse of(ErrorCode code, String message, Map<String, String> fields) {
        return new ApiErrorResponse(
                code.status(),
                code.error(),
                code.name(),
                message,
                fields == null ? Map.of() : fields,
                MDC.get(RequestIdFilter.MDC_KEY)
        );
    }
}
