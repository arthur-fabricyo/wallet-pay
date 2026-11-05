package ElFabrica.Wallet_pay.common.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.support.AbstractIntegrationTest;
import ElFabrica.Wallet_pay.support.MailpitClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SmtpEmailSenderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private MailpitClient mailpitClient;

    @BeforeEach
    void clearMailbox() {
        mailpitClient.clear();
    }

    @Test
    void sendsMessageThroughSmtp() {
        emailSender.send(new EmailMessage("ana@email.com", "Assunto", "Corpo"));

        assertThat(mailpitClient.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("ana@email.com");
            assertThat(message.subject()).isEqualTo("Assunto");
            assertThat(message.body()).contains("Corpo");
        });
    }

    @Test
    void rejectsSubjectWithLineBreaks() {
        assertThatThrownBy(() -> new EmailMessage("ana@email.com", "a\r\nBcc: x@y.com", "Corpo"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(mailpitClient.messages()).isEmpty();
    }
}
