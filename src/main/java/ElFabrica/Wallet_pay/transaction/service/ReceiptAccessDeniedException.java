package ElFabrica.Wallet_pay.transaction.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class ReceiptAccessDeniedException extends BusinessException {

    public ReceiptAccessDeniedException(String message) {
        super(ErrorCode.RECEIPT_ACCESS_DENIED, message);
    }
}
