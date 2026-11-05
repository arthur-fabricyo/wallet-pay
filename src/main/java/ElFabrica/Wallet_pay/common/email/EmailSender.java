package ElFabrica.Wallet_pay.common.email;

/** Application-wide port for sending e-mail; independent of any feature that uses it. */
public interface EmailSender {

    void send(EmailMessage message);
}
