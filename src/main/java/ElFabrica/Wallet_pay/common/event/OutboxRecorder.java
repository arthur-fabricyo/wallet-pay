package ElFabrica.Wallet_pay.common.event;

import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.ZoneOffset;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes events to the outbox in the caller's transaction, so they exist only if the business change commits. */
@Component
public class OutboxRecorder {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public OutboxRecorder(JdbcClient jdbcClient, JsonMapper jsonMapper, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(DomainEvent event) {
        String requestId = event.requestId() != null ? event.requestId() : MDC.get(RequestIdFilter.MDC_KEY);

        jdbcClient.sql("""
                        insert into outbox_events
                            (id, event_type, aggregate_id, payload, request_id, created_at, available_at)
                        values (:id, :type, :aggregateId, cast(:payload as jsonb), :requestId, :createdAt, :createdAt)
                        """)
                .param("id", event.eventId())
                .param("type", event.type())
                .param("aggregateId", event.aggregateId())
                .param("payload", jsonMapper.writeValueAsString(event.payload()))
                .param("requestId", requestId)
                .param("createdAt", clock.instant().atOffset(ZoneOffset.UTC))
                .update();
    }
}
