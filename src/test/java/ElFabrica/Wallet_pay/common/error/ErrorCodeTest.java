package ElFabrica.Wallet_pay.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import ElFabrica.Wallet_pay.auth.service.EmailNotVerifiedException;
import ElFabrica.Wallet_pay.auth.service.EmailVerificationTokenException;
import ElFabrica.Wallet_pay.auth.service.InvalidCredentialsException;
import ElFabrica.Wallet_pay.auth.service.InvalidPasswordPolicyException;
import ElFabrica.Wallet_pay.auth.service.InvalidRefreshTokenException;
import ElFabrica.Wallet_pay.auth.service.PasswordResetTokenException;
import ElFabrica.Wallet_pay.transaction.service.InsufficientBalanceException;
import ElFabrica.Wallet_pay.transaction.service.InvalidTransferException;
import ElFabrica.Wallet_pay.transaction.service.ReceiptAccessDeniedException;
import ElFabrica.Wallet_pay.transaction.service.ReceiptUnavailableException;
import ElFabrica.Wallet_pay.transaction.service.TransactionNotFoundException;
import ElFabrica.Wallet_pay.user.service.CnpjValidationUnavailableException;
import ElFabrica.Wallet_pay.user.service.DuplicateUserDataException;
import ElFabrica.Wallet_pay.user.service.InvalidDocumentException;
import ElFabrica.Wallet_pay.wallet.service.InvalidStatementQueryException;
import ElFabrica.Wallet_pay.wallet.service.WalletNotFoundException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void shouldExposeTheCompleteCatalogWithHttpStatus() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("VALIDATION_ERROR", 400);
        expected.put("MALFORMED_REQUEST", 400);
        expected.put("INVALID_PARAMETER", 400);
        expected.put("INVALID_AMOUNT", 400);
        expected.put("INVALID_DOCUMENT", 400);
        expected.put("EMAIL_VERIFICATION_TOKEN_INVALID", 400);
        expected.put("EMAIL_VERIFICATION_TOKEN_EXPIRED", 400);
        expected.put("EMAIL_VERIFICATION_TOKEN_ALREADY_USED", 400);
        expected.put("PASSWORD_RESET_TOKEN_INVALID", 400);
        expected.put("PASSWORD_RESET_TOKEN_EXPIRED", 400);
        expected.put("PASSWORD_RESET_TOKEN_ALREADY_USED", 400);
        expected.put("INVALID_PASSWORD_POLICY", 400);
        expected.put("INVALID_TRANSFER", 400);
        expected.put("INVALID_STATEMENT_QUERY", 400);
        expected.put("INVALID_LIMITS", 400);
        expected.put("IDEMPOTENCY_KEY_REQUIRED", 400);
        expected.put("IDEMPOTENCY_KEY_INVALID", 400);
        expected.put("UNAUTHORIZED", 401);
        expected.put("INVALID_CREDENTIALS", 401);
        expected.put("INVALID_REFRESH_TOKEN", 401);
        expected.put("ACCESS_DENIED", 403);
        expected.put("EMAIL_NOT_VERIFIED", 403);
        expected.put("RECEIPT_ACCESS_DENIED", 403);
        expected.put("ROUTE_NOT_FOUND", 404);
        expected.put("WALLET_NOT_FOUND", 404);
        expected.put("TRANSACTION_NOT_FOUND", 404);
        expected.put("METHOD_NOT_ALLOWED", 405);
        expected.put("DUPLICATE_USER_DATA", 409);
        expected.put("RECEIPT_UNAVAILABLE", 409);
        expected.put("BALANCE_CONFLICT", 409);
        expected.put("DATA_CONFLICT", 409);
        expected.put("UNSUPPORTED_MEDIA_TYPE", 415);
        expected.put("INSUFFICIENT_BALANCE", 422);
        expected.put("LIMIT_EXCEEDED", 422);
        expected.put("IDEMPOTENCY_KEY_REUSED", 422);
        expected.put("INTERNAL_ERROR", 500);
        expected.put("CNPJ_VALIDATION_UNAVAILABLE", 503);

        Map<String, Integer> actual = Arrays.stream(ErrorCode.values())
                .collect(Collectors.toMap(Enum::name, ErrorCode::status, (a, b) -> a, LinkedHashMap::new));

        assertThat(actual).containsExactlyEntriesOf(expected);
    }

    @Test
    void shouldDescribeStatusesWithTheTextsTheApiAlreadyUses() {
        assertThat(ErrorCode.INSUFFICIENT_BALANCE.error()).isEqualTo("Unprocessable Entity");
        assertThat(ErrorCode.VALIDATION_ERROR.error()).isEqualTo("Bad Request");
        assertThat(ErrorCode.UNAUTHORIZED.error()).isEqualTo("Unauthorized");
        assertThat(ErrorCode.ACCESS_DENIED.error()).isEqualTo("Forbidden");
        assertThat(ErrorCode.WALLET_NOT_FOUND.error()).isEqualTo("Not Found");
        assertThat(ErrorCode.METHOD_NOT_ALLOWED.error()).isEqualTo("Method Not Allowed");
        assertThat(ErrorCode.BALANCE_CONFLICT.error()).isEqualTo("Conflict");
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.error()).isEqualTo("Unsupported Media Type");
        assertThat(ErrorCode.INTERNAL_ERROR.error()).isEqualTo("Internal Server Error");
        assertThat(ErrorCode.CNPJ_VALIDATION_UNAVAILABLE.error()).isEqualTo("Service Unavailable");
    }

    @Test
    void shouldMapEveryExistingBusinessExceptionToItsCatalogCode() {
        assertThat(new EmailNotVerifiedException("x").getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        assertThat(new InvalidCredentialsException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(new InvalidRefreshTokenException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(new InvalidPasswordPolicyException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_PASSWORD_POLICY);
        assertThat(new EmailVerificationTokenException(ErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED, "x").getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED);
        assertThat(new PasswordResetTokenException(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED, "x").getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED);
        assertThat(new InvalidDocumentException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_DOCUMENT);
        assertThat(new DuplicateUserDataException("x").getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_USER_DATA);
        assertThat(new CnpjValidationUnavailableException("x", null).getErrorCode())
                .isEqualTo(ErrorCode.CNPJ_VALIDATION_UNAVAILABLE);
        assertThat(new InvalidTransferException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_TRANSFER);
        assertThat(new InsufficientBalanceException("x").getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(new TransactionNotFoundException("x").getErrorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
        assertThat(new ReceiptAccessDeniedException("x").getErrorCode()).isEqualTo(ErrorCode.RECEIPT_ACCESS_DENIED);
        assertThat(new ReceiptUnavailableException("x").getErrorCode()).isEqualTo(ErrorCode.RECEIPT_UNAVAILABLE);
        assertThat(new WalletNotFoundException("x").getErrorCode()).isEqualTo(ErrorCode.WALLET_NOT_FOUND);
        assertThat(new InvalidStatementQueryException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_STATEMENT_QUERY);
    }

    @Test
    void shouldKeepFieldDetailsOfDocumentAndPasswordErrors() {
        assertThat(new InvalidDocumentException("CPF invalido").getFields())
                .containsExactlyEntriesOf(Map.of("document", "CPF invalido"));
        assertThat(new InvalidPasswordPolicyException("Senha fraca").getFields())
                .containsExactlyEntriesOf(Map.of("newPassword", "Senha fraca"));
    }
}
