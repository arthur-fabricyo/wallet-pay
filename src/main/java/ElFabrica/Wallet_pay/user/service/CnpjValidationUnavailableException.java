package ElFabrica.Wallet_pay.user.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class CnpjValidationUnavailableException extends BusinessException {

    public CnpjValidationUnavailableException(String message, Throwable cause) {
        super(ErrorCode.CNPJ_VALIDATION_UNAVAILABLE, message, cause);
    }
}
