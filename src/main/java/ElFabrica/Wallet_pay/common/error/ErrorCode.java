package ElFabrica.Wallet_pay.common.error;

public enum ErrorCode {
    VALIDATION_ERROR(400),
    MALFORMED_REQUEST(400),
    INVALID_PARAMETER(400),
    INVALID_AMOUNT(400),
    INVALID_DOCUMENT(400),
    EMAIL_VERIFICATION_TOKEN_INVALID(400),
    EMAIL_VERIFICATION_TOKEN_EXPIRED(400),
    EMAIL_VERIFICATION_TOKEN_ALREADY_USED(400),
    PASSWORD_RESET_TOKEN_INVALID(400),
    PASSWORD_RESET_TOKEN_EXPIRED(400),
    PASSWORD_RESET_TOKEN_ALREADY_USED(400),
    INVALID_PASSWORD_POLICY(400),
    INVALID_TRANSFER(400),
    INVALID_STATEMENT_QUERY(400),
    INVALID_LIMITS(400),
    IDEMPOTENCY_KEY_REQUIRED(400),
    IDEMPOTENCY_KEY_INVALID(400),
    UNAUTHORIZED(401),
    INVALID_CREDENTIALS(401),
    INVALID_REFRESH_TOKEN(401),
    ACCESS_DENIED(403),
    EMAIL_NOT_VERIFIED(403),
    RECEIPT_ACCESS_DENIED(403),
    ROUTE_NOT_FOUND(404),
    WALLET_NOT_FOUND(404),
    TRANSACTION_NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    DUPLICATE_USER_DATA(409),
    RECEIPT_UNAVAILABLE(409),
    BALANCE_CONFLICT(409),
    DATA_CONFLICT(409),
    UNSUPPORTED_MEDIA_TYPE(415),
    INSUFFICIENT_BALANCE(422),
    LIMIT_EXCEEDED(422),
    IDEMPOTENCY_KEY_REUSED(422),
    INTERNAL_ERROR(500),
    CNPJ_VALIDATION_UNAVAILABLE(503);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }

    public String error() {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> throw new IllegalStateException("Status sem descricao: " + status);
        };
    }
}
