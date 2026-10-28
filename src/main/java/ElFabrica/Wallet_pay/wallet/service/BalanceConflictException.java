package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class BalanceConflictException extends BusinessException {

    public BalanceConflictException(String message) {
        super(ErrorCode.BALANCE_CONFLICT, message);
    }
}
