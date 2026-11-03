package ElFabrica.Wallet_pay.common.idempotency;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class IdempotencyKeyInvalidException extends BusinessException {

    public IdempotencyKeyInvalidException(String message) {
        super(ErrorCode.IDEMPOTENCY_KEY_INVALID, message);
    }
}
