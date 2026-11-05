package ElFabrica.Wallet_pay.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.common.event.OutboxPublisherJob;
import ElFabrica.Wallet_pay.notification.push.PushMessage;
import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.CapturingPushGateway;
import ElFabrica.Wallet_pay.support.MailpitClient;
import ElFabrica.Wallet_pay.support.TestUser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

/** Real broker, real outbox job and real SMTP (Mailpit); only the push gateway is captured. */
class TransferNotificationEndToEndTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxPublisherJob outboxJob;

    @Autowired
    private CapturingPushGateway pushGateway;

    @Autowired
    private MailpitClient mailpitClient;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Value("${walletpay.events.namespace}")
    private String namespace;

    @BeforeEach
    void resetState() {
        pushGateway.sent.clear();
        mailpitClient.clear();
        rabbitAdmin.purgeQueue(namespace + ".walletpay.notifications.TransferCompleted", true);
    }

    @Test
    void transferNotifiesBothByPushAndEmailsOnlyOptedInUser() throws Exception {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        enableEmail(bruno);

        transfer(ana, bruno);
        assertThat(outboxJob.publishPending()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(pushGateway.sent).extracting(PushMessage::userId)
                    .containsExactlyInAnyOrder(ana.userId(), bruno.userId());
            assertThat(mailpitClient.messages()).singleElement()
                    .satisfies(message -> assertThat(message.to()).isEqualTo(bruno.email()));
        });
    }

    @Test
    void redeliveredEventDoesNotSendSecondEmail() throws Exception {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        enableEmail(bruno);
        transfer(ana, bruno);
        outboxJob.publishPending();
        await().atMost(Duration.ofSeconds(15)).until(() -> mailpitClient.messages().size() == 1);

        jdbcTemplate.update("update outbox_events set published_at = null");
        assertThat(outboxJob.publishPending()).isEqualTo(1);
        Thread.sleep(2000);

        assertThat(mailpitClient.messages()).hasSize(1);
        assertThat(List.copyOf(pushGateway.sent)).hasSize(2);
    }

    private void enableEmail(TestUser user) throws Exception {
        mockMvc.perform(put("/notifications/preferences/EMAIL")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk());
    }

    private void transfer(TestUser sender, TestUser receiver) throws Exception {
        mockMvc.perform(post("/transactions/transfers")
                        .header("Authorization", bearer(sender))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\": \"%s\", \"amount\": \"100.00\"}".formatted(receiver.userId())))
                .andExpect(status().isCreated());
    }
}
