package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class InvalidTransferException extends BusinessException {

    public InvalidTransferException(String message) {
        super(ErrorCode.INVALID_TRANSFER, message);
    }
}
