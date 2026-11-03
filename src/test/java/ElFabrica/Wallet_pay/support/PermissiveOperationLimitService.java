package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.wallet.service.OperationLimitService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PermissiveOperationLimitService extends OperationLimitService {

    public PermissiveOperationLimitService() {
        super(null, null, null);
    }

    @Override
    public void checkTransfer(UUID walletId, BigDecimal amount, Instant now) {
    }

    @Override
    public void checkDeposit(UUID walletId, BigDecimal amount, Instant now) {
    }
}
