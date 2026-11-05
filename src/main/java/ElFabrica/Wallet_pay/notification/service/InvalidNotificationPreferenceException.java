package ElFabrica.Wallet_pay.notification.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class InvalidNotificationPreferenceException extends BusinessException {

    public InvalidNotificationPreferenceException(String message) {
        super(ErrorCode.INVALID_PARAMETER, message);
    }
}
