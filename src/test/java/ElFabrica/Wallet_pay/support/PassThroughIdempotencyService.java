package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.common.idempotency.IdempotencyRequest;
import ElFabrica.Wallet_pay.common.idempotency.IdempotencyService;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import java.util.function.Supplier;

public class PassThroughIdempotencyService extends IdempotencyService {

    public IdempotencyRequest lastRequest;

    public PassThroughIdempotencyService() {
        super(null, null, null);
    }

    @Override
    public <T> IdempotentResult<T> execute(IdempotencyRequest request, Class<T> resultType, Supplier<T> action) {
        this.lastRequest = request;
        return new IdempotentResult<>(action.get(), false);
    }
}
