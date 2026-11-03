package ElFabrica.Wallet_pay.common.money;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class InvalidAmountException extends BusinessException {

    public InvalidAmountException(String message) {
        super(ErrorCode.INVALID_AMOUNT, message);
    }
}
