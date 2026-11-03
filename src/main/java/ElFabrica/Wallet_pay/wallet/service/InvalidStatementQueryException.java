package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;

public class InvalidStatementQueryException extends BusinessException {

    public InvalidStatementQueryException(String message) {
        super(ErrorCode.INVALID_STATEMENT_QUERY, message);
    }
}
