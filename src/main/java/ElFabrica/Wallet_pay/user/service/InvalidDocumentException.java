package ElFabrica.Wallet_pay.user.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.util.Map;

public class InvalidDocumentException extends BusinessException {

    public InvalidDocumentException(String message) {
        super(ErrorCode.INVALID_DOCUMENT, message);
    }

    @Override
    public Map<String, String> getFields() {
        return Map.of("document", getMessage());
    }
}
