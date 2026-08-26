package ElFabrica.Wallet_pay.common.error;

import ElFabrica.Wallet_pay.auth.service.EmailNotVerifiedException;
import ElFabrica.Wallet_pay.auth.service.EmailVerificationTokenException;
import ElFabrica.Wallet_pay.auth.service.InvalidPasswordPolicyException;
import ElFabrica.Wallet_pay.auth.service.InvalidCredentialsException;
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
import ElFabrica.Wallet_pay.wallet.service.WalletNotFoundException;
import ElFabrica.Wallet_pay.wallet.service.InvalidStatementQueryException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        "Dados invalidos",
                        fields
                ));
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidDocument(InvalidDocumentException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        exception.getMessage(),
                        Map.of("document", exception.getMessage())
                ));
    }

    @ExceptionHandler(DuplicateUserDataException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateUserData(DuplicateUserDataException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "Conflict",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    ResponseEntity<ApiErrorResponse> handleEmailNotVerified(EmailNotVerifiedException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        "EMAIL_NOT_VERIFIED",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(EmailVerificationTokenException.class)
    ResponseEntity<ApiErrorResponse> handleEmailVerificationToken(EmailVerificationTokenException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        exception.getCode(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(PasswordResetTokenException.class)
    ResponseEntity<ApiErrorResponse> handlePasswordResetToken(PasswordResetTokenException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        exception.getCode(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidPasswordPolicyException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidPasswordPolicy(InvalidPasswordPolicyException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        exception.getMessage(),
                        Map.of("newPassword", exception.getMessage())
                ));
    }

    @ExceptionHandler(CnpjValidationUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleCnpjValidationUnavailable(CnpjValidationUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidTransferException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidTransfer(InvalidTransferException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    ResponseEntity<ApiErrorResponse> handleInsufficientBalance(InsufficientBalanceException exception) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(
                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "Unprocessable Entity",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleTransactionNotFound(TransactionNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "Not Found",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(ReceiptAccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleReceiptAccessDenied(ReceiptAccessDeniedException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        "Forbidden",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(ReceiptUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleReceiptUnavailable(ReceiptUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "Conflict",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleWalletNotFound(WalletNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "Not Found",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InvalidStatementQueryException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidStatementQuery(InvalidStatementQueryException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad Request",
                        exception.getMessage()
                ));
    }
}
