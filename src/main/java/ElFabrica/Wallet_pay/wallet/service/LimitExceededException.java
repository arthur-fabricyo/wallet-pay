package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import ElFabrica.Wallet_pay.common.money.Money;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class LimitExceededException extends BusinessException {

    private final LimitType limitType;
    private final BigDecimal limit;
    private final BigDecimal available;

    public LimitExceededException(LimitType limitType, String message, BigDecimal limit, BigDecimal available) {
        super(ErrorCode.LIMIT_EXCEEDED, message);
        this.limitType = limitType;
        this.limit = limit;
        this.available = available;
    }

    public LimitType getLimitType() {
        return limitType;
    }

    @Override
    public Map<String, String> getFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("limitType", limitType.name());
        fields.put("limit", Money.format(limit));
        fields.put("available", Money.format(available));
        return fields;
    }
}
