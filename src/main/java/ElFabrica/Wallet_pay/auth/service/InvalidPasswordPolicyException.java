package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.util.Map;

public class InvalidPasswordPolicyException extends BusinessException {

    public InvalidPasswordPolicyException(String message) {
        super(ErrorCode.INVALID_PASSWORD_POLICY, message);
    }

    @Override
    public Map<String, String> getFields() {
        return Map.of("newPassword", getMessage());
    }
}
