package ElFabrica.Wallet_pay.common.event.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.common.event.EventHandler;
import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@Import(RabbitEventAdapterIntegrationTest.HandlerConfig.class)
class RabbitEventAdapterIntegrationTest extends AbstractIntegrationTest {

    static class RecordingHandler implements EventHandler {
        final String type;
        final List<DomainEvent> received = new CopyOnWriteArrayList<>();
        final AtomicReference<String> mdcRequestId = new AtomicReference<>();

        RecordingHandler(String type) {
            this.type = type;
        }

        @Override
        public String consumerName() {
            return "adapter-test";
        }

        @Override
        public String eventType() {
            return type;
        }

        @Override
        public void handle(DomainEvent event) {
            mdcRequestId.set(MDC.get(RequestIdFilter.MDC_KEY));
            received.add(event);
        }
    }

    static class FailingHandler implements EventHandler {
        @Override
        public String consumerName() {
            return "adapter-test";
        }

        @Override
        public String eventType() {
            return "AdapterFailing";
        }

        @Override
        public void handle(DomainEvent event) {
            throw new IllegalStateException("always fails");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerConfig {
        @Bean
        RecordingHandler adapterSampleHandler() {
            return new RecordingHandler("AdapterSample");
        }

        @Bean
        RecordingHandler adapterOtherHandler() {
            return new RecordingHandler("AdapterOther");
        }

        @Bean
        FailingHandler adapterFailingHandler() {
            return new FailingHandler();
        }
    }

    @Autowired
    private RabbitEventPublisher publisher;

    @Autowired
    private RecordingHandler adapterSampleHandler;

    @Autowired
    private RecordingHandler adapterOtherHandler;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private RabbitTopology topology;

    private String dlq() {
        return topology.deadLetterQueueName("adapter-test", "AdapterFailing");
    }

    @BeforeEach
    void purge() {
        for (String queue : List.of(
                topology.queueName("adapter-test", "AdapterSample"),
                topology.queueName("adapter-test", "AdapterOther"),
                topology.queueName("adapter-test", "AdapterFailing"),
                dlq())) {
            rabbitAdmin.purgeQueue(queue, true);
        }
        adapterSampleHandler.received.clear();
        adapterSampleHandler.mdcRequestId.set(null);
        adapterOtherHandler.received.clear();
    }

    private DomainEvent event(String type, String requestId) {
        return new DomainEvent(UUID.randomUUID(), type, Instant.parse("2026-01-01T10:00:00Z"), UUID.randomUUID(),
                requestId, jsonMapper.valueToTree(Map.of("amount", "100.00")));
    }

    @Test
    void publishedEventReachesMatchingHandler() {
        DomainEvent event = event("AdapterSample", "req-1");

        publisher.publish(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(adapterSampleHandler.received).singleElement().satisfies(received -> {
                    assertThat(received.eventId()).isEqualTo(event.eventId());
                    assertThat(received.type()).isEqualTo("AdapterSample");
                    assertThat(received.aggregateId()).isEqualTo(event.aggregateId());
                    assertThat(received.occurredAt()).isEqualTo(event.occurredAt());
                    assertThat(received.payload().path("amount").asString()).isEqualTo("100.00");
                }));
    }

    @Test
    void handlerForOtherTypeDoesNotReceiveIt() throws Exception {
        publisher.publish(event("AdapterSample", null));

        await().atMost(Duration.ofSeconds(10)).until(() -> !adapterSampleHandler.received.isEmpty());
        Thread.sleep(500);
        assertThat(adapterOtherHandler.received).isEmpty();
    }

    @Test
    void failingHandlerMessageEndsUpInDeadLetterQueue() {
        publisher.publish(event("AdapterFailing", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Message dead = rabbitTemplate.receive(dlq());
            assertThat(dead).isNotNull();
        });
    }

    @Test
    void publishFailsWhenTheBrokerDoesNotAcceptTheMessage() {
        rabbitAdmin.deleteExchange(topology.exchange());
        try {
            assertThatThrownBy(() -> publisher.publish(event("AdapterSample", null)))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            rabbitAdmin.initialize();
        }
    }

    @Test
    void requestIdIsAvailableInMdcWhileHandling() {
        publisher.publish(event("AdapterSample", "req-abc"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(adapterSampleHandler.received).hasSize(1);
            assertThat(adapterSampleHandler.mdcRequestId.get()).isEqualTo("req-abc");
        });
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
