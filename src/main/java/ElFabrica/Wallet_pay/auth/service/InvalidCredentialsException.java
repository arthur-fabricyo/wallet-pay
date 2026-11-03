package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
