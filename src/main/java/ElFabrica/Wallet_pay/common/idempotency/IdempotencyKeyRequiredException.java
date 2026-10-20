package ElFabrica.Wallet_pay.common.idempotency;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class IdempotencyKeyRequiredException extends BusinessException {

    public IdempotencyKeyRequiredException(String message) {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, message);
    }
}
