package ElFabrica.Wallet_pay.common.event;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Broker-neutral event envelope; serialized as JSON by whichever adapter carries it. */
public record DomainEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        UUID aggregateId,
        String requestId,
        JsonNode payload
) {
}
