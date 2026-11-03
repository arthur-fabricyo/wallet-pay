package ElFabrica.Wallet_pay.audit;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEntry(
        AuditAction action,
        UUID actorUserId,
        UUID resourceId,
        ErrorCode failureCode,
        Map<String, String> metadata
) {
    public AuditEntry {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AuditEntry success(AuditAction action, UUID resourceId, Map<String, String> metadata) {
        return new AuditEntry(action, null, resourceId, null, metadata);
    }

    public static AuditEntry failure(
            AuditAction action,
            UUID resourceId,
            ErrorCode failureCode,
            Map<String, String> metadata
    ) {
        return new AuditEntry(action, null, resourceId, failureCode, metadata);
    }

    public AuditEntry withActor(UUID actorUserId) {
        return new AuditEntry(action, actorUserId, resourceId, failureCode, metadata);
    }

    public AuditOutcome outcome() {
        return failureCode == null ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE;
    }

    static final int MAX_RAW_VALUE_LENGTH = 32;

    /** Bounds a raw client value (e.g. an amount that failed validation) before it goes into append-only metadata. */
    public static String truncateRaw(String value) {
        if (value == null || value.length() <= MAX_RAW_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_RAW_VALUE_LENGTH) + "...";
    }

    public static Map<String, String> metadata(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("metadata exige pares de chave e valor");
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                metadata.put((String) keysAndValues[i], value instanceof Enum<?> constant ? constant.name() : value.toString());
            }
        }
        return metadata;
    }
}
