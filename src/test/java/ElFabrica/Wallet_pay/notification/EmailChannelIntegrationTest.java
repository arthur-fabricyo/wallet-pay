package ElFabrica.Wallet_pay.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.notification.channel.EmailChannel;
import ElFabrica.Wallet_pay.notification.service.NotificationPreferenceService;
import ElFabrica.Wallet_pay.notification.service.TransferCompletedHandler;
import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.MailpitClient;
import ElFabrica.Wallet_pay.support.MailpitClient.MailpitMessage;
import ElFabrica.Wallet_pay.support.TestUser;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class EmailChannelIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EmailChannel emailChannel;

    @Autowired
    private TransferCompletedHandler handler;

    @Autowired
    private NotificationPreferenceService preferences;

    @Autowired
    private MailpitClient mailpitClient;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void clearMailbox() {
        mailpitClient.clear();
    }

    @Test
    void deliversToUsersEmailThroughSmtp() {
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");

        emailChannel.deliver(new Notification(UUID.randomUUID(), bruno.userId(), Notification.TRANSFER_RECEIVED,
                "Transferência recebida", "Você recebeu R$ 100,00 de Ana Souza."));

        assertThat(mailpitClient.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo(bruno.email());
            assertThat(message.subject()).isEqualTo("Você recebeu uma transferência");
            assertThat(message.body()).contains("Você recebeu R$ 100,00 de Ana Souza.");
        });
    }

    @Test
    void userNameWithLineBreaksAndHtmlCannotBreakEmail() {
        TestUser evil = createVerifiedUser("Bob\r\nBcc: x@y.com <b>", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        preferences.update(bruno.userId(), NotificationChannelType.EMAIL, true);
        UUID transactionId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(UUID.randomUUID(), "TransferCompleted",
                Instant.parse("2026-11-04T17:30:00Z"), transactionId, null, jsonMapper.valueToTree(Map.of(
                "transactionId", transactionId, "amount", "100.00", "currency", "BRL",
                "senderUserId", evil.userId(), "receiverUserId", bruno.userId())));

        handler.handle(event);

        assertThat(mailpitClient.messages()).singleElement().satisfies((MailpitMessage message) -> {
            assertThat(message.to()).isEqualTo(bruno.email());
            assertThat(message.subject()).isEqualTo("Você recebeu uma transferência");
            // The name stays on a single line (the SMTP stack only adds a trailing line terminator).
            assertThat(message.body().strip()).doesNotContain("\r", "\n")
                    .contains("de Bob Bcc: x@y.com <b> em");
        });
    }
}
