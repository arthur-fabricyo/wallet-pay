package ElFabrica.Wallet_pay.common.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Publishes unpublished outbox rows (at-least-once). Safe to run on several instances: rows are locked with skip locked. */
@Component
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final EventPublisher publisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OutboxPublisherJob(JdbcClient jdbcClient, JsonMapper jsonMapper, EventPublisher publisher,
                              TransactionTemplate transactionTemplate, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.publisher = publisher;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${walletpay.events.outbox.poll-delay:PT2S}")
    public void scheduledPublish() {
        publishPending();
    }

    /** Publishes one batch in creation order; stops at the first failure to preserve order. Returns how many went out. */
    public int publishPending() {
        Integer published = transactionTemplate.execute(status -> {
            List<UUID> ids = jdbcClient.sql("""
                            select id from outbox_events
                            where published_at is null
                            order by created_at, id
                            limit :limit
                            for update skip locked
                            """)
                    .param("limit", BATCH_SIZE)
                    .query(UUID.class)
                    .list();

            int count = 0;
            for (UUID id : ids) {
                DomainEvent event = load(id);
                try {
                    publisher.publish(event);
                } catch (RuntimeException e) {
                    markFailed(id, e);
                    log.warn("Failed to publish outbox event {}: {}", id, e.toString());
                    break;
                }
                jdbcClient.sql("update outbox_events set published_at = :now where id = :id")
                        .param("now", clock.instant().atOffset(ZoneOffset.UTC))
                        .param("id", id)
                        .update();
                count++;
            }
            return count;
        });
        return published == null ? 0 : published;
    }

    private DomainEvent load(UUID id) {
        return jdbcClient.sql("""
                        select id, event_type, aggregate_id, payload::text as payload, request_id, created_at
                        from outbox_events where id = :id
                        """)
                .param("id", id)
                .query(this::map)
                .single();
    }

    private DomainEvent map(ResultSet rs, int row) throws SQLException {
        return new DomainEvent(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("request_id"),
                jsonMapper.readTree(rs.getString("payload"))
        );
    }

    private void markFailed(UUID id, RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        jdbcClient.sql("update outbox_events set attempts = attempts + 1, last_error = :error where id = :id")
                .param("error", message)
                .param("id", id)
                .update();
    }
}
