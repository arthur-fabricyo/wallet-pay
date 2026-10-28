package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class PasswordResetTokenException extends BusinessException {

    public PasswordResetTokenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
