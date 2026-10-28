package ElFabrica.Wallet_pay.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {

    private final AtomicReference<Instant> fixedInstant = new AtomicReference<>();

    public void setInstant(Instant instant) {
        fixedInstant.set(instant);
    }

    public void reset() {
        fixedInstant.set(null);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock usa sempre UTC");
    }

    @Override
    public Instant instant() {
        Instant instant = fixedInstant.get();
        return instant != null ? instant : Instant.now();
    }
}
