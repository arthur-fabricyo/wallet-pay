package ElFabrica.Wallet_pay.notification.channel;

import ElFabrica.Wallet_pay.common.email.EmailMessage;
import ElFabrica.Wallet_pay.common.email.EmailSender;
import ElFabrica.Wallet_pay.notification.Notification;
import ElFabrica.Wallet_pay.notification.NotificationChannelType;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Sends the notification to the user's registered e-mail. The subject is fixed per notification kind, so no user
 * data can reach the headers.
 */
@Component
public class EmailChannel implements NotificationChannel {

    private final EmailSender emailSender;
    private final UserRepository userRepository;

    public EmailChannel(EmailSender emailSender, UserRepository userRepository) {
        this.emailSender = emailSender;
        this.userRepository = userRepository;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void deliver(Notification notification) {
        String to = userRepository.findById(notification.userId())
                .orElseThrow(() -> new IllegalStateException("Unknown user " + notification.userId()))
                .getEmail();
        emailSender.send(new EmailMessage(to, subjectFor(notification.kind()), notification.body()));
    }

    private static String subjectFor(String kind) {
        return switch (kind) {
            case Notification.TRANSFER_RECEIVED -> "Você recebeu uma transferência";
            case Notification.TRANSFER_SENT -> "Transferência enviada";
            default -> "Notificação do Wallet Pay";
        };
    }
}
