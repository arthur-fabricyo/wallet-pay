package ElFabrica.Wallet_pay.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class InfrastructureSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MailpitClient mailpitClient;

    @Test
    void rabbitConnectionIsAvailable() {
        Boolean open = rabbitTemplate.execute(channel -> channel.isOpen());

        assertThat(open).isTrue();
    }

    @Test
    void mailpitStartsEmpty() {
        mailpitClient.clear();

        assertThat(mailpitClient.messages()).isEmpty();
    }
}
