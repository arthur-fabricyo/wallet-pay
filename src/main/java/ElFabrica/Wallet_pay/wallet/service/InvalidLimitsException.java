package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvalidLimitsException extends BusinessException {

    private final Map<String, String> fields;

    public InvalidLimitsException(String message, Map<String, String> fields) {
        super(ErrorCode.INVALID_LIMITS, message);
        this.fields = new LinkedHashMap<>(fields);
    }

    @Override
    public Map<String, String> getFields() {
        return fields;
    }
}
