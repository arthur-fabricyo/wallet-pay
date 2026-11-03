package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import java.util.ArrayList;
import java.util.List;

public class RecordingAuditLogger extends AuditLogger {

    public final List<AuditEntry> recorded = new ArrayList<>();
    public final List<AuditEntry> failures = new ArrayList<>();

    public RecordingAuditLogger() {
        super(null, null, null);
    }

    @Override
    public void record(AuditEntry entry) {
        recorded.add(entry);
    }

    @Override
    public void recordFailure(AuditEntry entry) {
        failures.add(entry);
    }
}
