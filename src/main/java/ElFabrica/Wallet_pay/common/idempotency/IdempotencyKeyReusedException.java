package ElFabrica.Wallet_pay.common.idempotency;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException(String message) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, message);
    }
}
