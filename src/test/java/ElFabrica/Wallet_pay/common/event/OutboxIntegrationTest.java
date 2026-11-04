package ElFabrica.Wallet_pay.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(OutboxIntegrationTest.PublisherConfig.class)
class OutboxIntegrationTest extends AbstractIntegrationTest {

    static class ControllableEventPublisher implements EventPublisher {
        final List<DomainEvent> published = new CopyOnWriteArrayList<>();
        final AtomicBoolean failNext = new AtomicBoolean();

        @Override
        public void publish(DomainEvent event) {
            if (failNext.getAndSet(false)) {
                throw new IllegalStateException("broker down");
            }
            published.add(event);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PublisherConfig {
        @Bean
        @Primary
        ControllableEventPublisher controllableEventPublisher() {
            return new ControllableEventPublisher();
        }
    }

    @Autowired
    private OutboxRecorder outboxRecorder;

    @Autowired
    private OutboxPublisherJob job;

    @Autowired
    private ControllableEventPublisher publisher;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetPublisher() {
        publisher.published.clear();
        publisher.failNext.set(false);
    }

    private DomainEvent event(String type) {
        JsonNode payload = jsonMapper.valueToTree(Map.of("hello", "world"));
        return new DomainEvent(UUID.randomUUID(), type, Instant.now(), UUID.randomUUID(), null, payload);
    }

    private long rowCount() {
        return jdbcTemplate.queryForObject("select count(*) from outbox_events", Long.class);
    }

    @Test
    void recordsEventInsideTransaction() {
        DomainEvent event = event("Sample");

        transactionTemplate.executeWithoutResult(status -> outboxRecorder.record(event));

        assertThat(rowCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where id = ? and published_at is null and event_type = 'Sample'",
                Long.class, event.eventId())).isEqualTo(1);
    }

    @Test
    void recordFailsOutsideTransaction() {
        assertThatThrownBy(() -> outboxRecorder.record(event("Sample")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rollbackLeavesNoEvent() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            outboxRecorder.record(event("Sample"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowCount()).isZero();
    }

    @Test
    void publishPendingPublishesAndMarksRows() {
        DomainEvent event = event("Sample");
        transactionTemplate.executeWithoutResult(status -> outboxRecorder.record(event));

        int count = job.publishPending();

        assertThat(count).isEqualTo(1);
        assertThat(publisher.published).singleElement().satisfies(published -> {
            assertThat(published.eventId()).isEqualTo(event.eventId());
            assertThat(published.type()).isEqualTo("Sample");
            assertThat(published.payload().path("hello").asString()).isEqualTo("world");
        });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where published_at is not null", Long.class)).isEqualTo(1);
        assertThat(job.publishPending()).isZero();
    }

    @Test
    void brokerFailureKeepsRowAndRetriesLater() {
        DomainEvent event = event("Sample");
        transactionTemplate.executeWithoutResult(status -> outboxRecorder.record(event));
        publisher.failNext.set(true);

        assertThat(job.publishPending()).isZero();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select published_at, attempts, last_error from outbox_events where id = ?", event.eventId());
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat((String) row.get("last_error")).contains("broker down");

        assertThat(job.publishPending()).isEqualTo(1);
        assertThat(publisher.published).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select published_at is not null from outbox_events where id = ?", Boolean.class, event.eventId()))
                .isTrue();
    }

    @Test
    void publishesInCreationOrder() {
        List<DomainEvent> events = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            DomainEvent event = event("Sample");
            events.add(event);
            clock.setInstant(base.plusSeconds(i));
            transactionTemplate.executeWithoutResult(status -> outboxRecorder.record(event));
        }

        job.publishPending();

        assertThat(publisher.published).extracting(DomainEvent::eventId)
                .containsExactlyElementsOf(events.stream().map(DomainEvent::eventId).toList());
    }

    @Test
    void fillsRequestIdFromMdcWhenMissing() {
        org.slf4j.MDC.put(ElFabrica.Wallet_pay.common.web.RequestIdFilter.MDC_KEY, "req-123");
        try {
            transactionTemplate.executeWithoutResult(status -> outboxRecorder.record(event("Sample")));
        } finally {
            org.slf4j.MDC.clear();
        }

        job.publishPending();

        assertThat(publisher.published).singleElement()
                .satisfies(published -> assertThat(published.requestId()).isEqualTo("req-123"));
    }
}
