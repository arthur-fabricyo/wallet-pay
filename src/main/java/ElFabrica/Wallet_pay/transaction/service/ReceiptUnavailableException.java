package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class ReceiptUnavailableException extends BusinessException {

    public ReceiptUnavailableException(String message) {
        super(ErrorCode.RECEIPT_UNAVAILABLE, message);
    }
}
