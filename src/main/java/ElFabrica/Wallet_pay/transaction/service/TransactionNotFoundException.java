package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class TransactionNotFoundException extends BusinessException {

    public TransactionNotFoundException(String message) {
        super(ErrorCode.TRANSACTION_NOT_FOUND, message);
    }
}
