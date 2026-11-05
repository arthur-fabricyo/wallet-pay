package ElFabrica.Wallet_pay.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.notification.channel.EmailChannel;
import ElFabrica.Wallet_pay.notification.push.PushMessage;
import ElFabrica.Wallet_pay.notification.service.TransferCompletedHandler;
import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.CapturingPushGateway;
import ElFabrica.Wallet_pay.support.TestUser;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

class TransferNotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransferCompletedHandler handler;

    @Autowired
    private CapturingPushGateway pushGateway;

    @MockitoSpyBean
    private EmailChannel emailChannel;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void resetCaptures() {
        pushGateway.sent.clear();
        Mockito.reset(emailChannel);
    }

    private DomainEvent transferEvent(UUID senderUserId, UUID receiverUserId) {
        UUID transactionId = UUID.randomUUID();
        return new DomainEvent(UUID.randomUUID(), "TransferCompleted", Instant.parse("2026-11-04T17:30:00Z"),
                transactionId, null, jsonMapper.valueToTree(Map.of(
                "transactionId", transactionId,
                "amount", "100.00",
                "currency", "BRL",
                "senderUserId", senderUserId,
                "receiverUserId", receiverUserId)));
    }

    private void enableEmail(TestUser user) throws Exception {
        mockMvc.perform(put("/notifications/preferences/EMAIL")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk());
    }

    @Test
    void bothSenderAndReceiverGetPush() {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "50.00");

        handler.handle(transferEvent(ana.userId(), bruno.userId()));

        assertThat(pushGateway.sent).hasSize(2);
        PushMessage toSender = pushGateway.sent.stream().filter(m -> m.userId().equals(ana.userId())).findFirst()
                .orElseThrow();
        PushMessage toReceiver = pushGateway.sent.stream().filter(m -> m.userId().equals(bruno.userId()))
                .findFirst().orElseThrow();
        assertThat(toSender.title()).isEqualTo("Transferência enviada");
        assertThat(toSender.body()).contains("R$ 100,00").contains("Bruno Lima").contains("04/11/2026 14:30");
        assertThat(toReceiver.title()).isEqualTo("Transferência recebida");
        assertThat(toReceiver.body()).contains("R$ 100,00").contains("Ana Souza").contains("04/11/2026 14:30");
        for (PushMessage message : pushGateway.sent) {
            assertThat(message.title() + message.body())
                    .doesNotContain("@email.com")
                    .doesNotContain("500,00")
                    .doesNotContain("50,00");
        }
    }

    @Test
    void emailChannelOnlyWhenPreferenceEnabled() throws Exception {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        enableEmail(bruno);

        handler.handle(transferEvent(ana.userId(), bruno.userId()));

        ArgumentCaptor<Notification> delivered = ArgumentCaptor.forClass(Notification.class);
        verify(emailChannel, times(1)).deliver(delivered.capture());
        assertThat(delivered.getValue().userId()).isEqualTo(bruno.userId());
    }

    @Test
    void redeliveryDoesNotDuplicateNotifications() throws Exception {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        enableEmail(bruno);
        DomainEvent event = transferEvent(ana.userId(), bruno.userId());

        handler.handle(event);
        handler.handle(event);

        assertThat(pushGateway.sent).hasSize(2);
        verify(emailChannel, times(1)).deliver(any());
    }

    @Test
    void failureInOneChannelKeepsDeliveredChannelsAndRetriesOnlyPending() throws Exception {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        enableEmail(bruno);
        DomainEvent event = transferEvent(ana.userId(), bruno.userId());
        doThrow(new IllegalStateException("smtp down")).doCallRealMethod().when(emailChannel).deliver(any());

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(IllegalStateException.class);
        handler.handle(event);

        assertThat(pushGateway.sent).hasSize(2);
        verify(emailChannel, times(2)).deliver(any());
    }

    @Test
    void failingChannelOfTheSenderDoesNotBlockTheReceiversNotification() throws Exception {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");
        TestUser bruno = createVerifiedUser("Bruno Lima", "0.00");
        enableEmail(ana);
        doThrow(new IllegalStateException("smtp down")).when(emailChannel).deliver(any());

        assertThatThrownBy(() -> handler.handle(transferEvent(ana.userId(), bruno.userId())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(pushGateway.sent).extracting(PushMessage::userId)
                .containsExactlyInAnyOrder(ana.userId(), bruno.userId());
    }

    @Test
    void unknownUserIsDroppedWithoutException() {
        TestUser ana = createVerifiedUser("Ana Souza", "500.00");

        handler.handle(transferEvent(ana.userId(), UUID.randomUUID()));

        assertThat(pushGateway.sent).isEmpty();
    }
}
