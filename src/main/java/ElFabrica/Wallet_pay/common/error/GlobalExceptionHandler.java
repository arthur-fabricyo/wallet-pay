package ElFabrica.Wallet_pay.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception) {
        LOGGER.warn("Erro de negocio {}: {}", exception.getErrorCode(), exception.getMessage());
        return error(exception.getErrorCode(), exception.getMessage(), exception.getFields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return error(ErrorCode.VALIDATION_ERROR, "Dados invalidos", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(HttpMessageNotReadableException exception) {
        return error(ErrorCode.MALFORMED_REQUEST, "Corpo da requisicao invalido", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(ErrorCode.INVALID_PARAMETER, "Parametro invalido", Map.of(exception.getName(), "Valor invalido"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return error(
                ErrorCode.INVALID_PARAMETER,
                "Parametro obrigatorio ausente",
                Map.of(exception.getParameterName(), "Parametro obrigatorio")
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        if (IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(exception.getHeaderName())) {
            return error(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key e obrigatorio", Map.of());
        }
        return error(
                ErrorCode.INVALID_PARAMETER,
                "Header obrigatorio ausente",
                Map.of(exception.getHeaderName(), "Header obrigatorio")
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return error(ErrorCode.METHOD_NOT_ALLOWED, "Metodo HTTP nao suportado", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content-Type nao suportado", Map.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiErrorResponse> handleRouteNotFound(Exception exception) {
        return error(ErrorCode.ROUTE_NOT_FOUND, "Rota nao encontrada", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
        LOGGER.warn("Violacao de integridade nao tratada", exception);
        return error(ErrorCode.DATA_CONFLICT, "Conflito de dados", Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException exception) {
        LOGGER.warn("Conflito de versao fora de operacao de saldo", exception);
        return error(ErrorCode.DATA_CONFLICT, "Conflito de dados", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        LOGGER.error("Erro inesperado", exception);
        return error(ErrorCode.INTERNAL_ERROR, "Erro interno, informe o requestId ao suporte", Map.of());
    }

    static ResponseEntity<ApiErrorResponse> error(ErrorCode code, String message, Map<String, String> fields) {
        return ResponseEntity.status(code.status()).body(ApiErrorResponse.of(code, message, fields));
    }
}
