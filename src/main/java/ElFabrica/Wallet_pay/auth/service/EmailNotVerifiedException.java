package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class EmailNotVerifiedException extends BusinessException {

    public EmailNotVerifiedException(String message) {
        super(ErrorCode.EMAIL_NOT_VERIFIED, message);
    }
}
