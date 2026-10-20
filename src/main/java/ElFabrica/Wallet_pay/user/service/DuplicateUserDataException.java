package ElFabrica.Wallet_pay.user.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class DuplicateUserDataException extends BusinessException {

    public DuplicateUserDataException(String message) {
        super(ErrorCode.DUPLICATE_USER_DATA, message);
    }
}
