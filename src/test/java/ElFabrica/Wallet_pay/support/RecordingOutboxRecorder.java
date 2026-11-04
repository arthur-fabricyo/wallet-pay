package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.common.event.OutboxRecorder;
import java.util.ArrayList;
import java.util.List;

public class RecordingOutboxRecorder extends OutboxRecorder {

    public final List<DomainEvent> recorded = new ArrayList<>();

    public RecordingOutboxRecorder() {
        super(null, null, null);
    }

    @Override
    public void record(DomainEvent event) {
        recorded.add(event);
    }
}
