package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class WalletNotFoundException extends BusinessException {

    public WalletNotFoundException(String message) {
        super(ErrorCode.WALLET_NOT_FOUND, message);
    }
}
