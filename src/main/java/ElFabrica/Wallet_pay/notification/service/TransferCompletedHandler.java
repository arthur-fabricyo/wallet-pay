package ElFabrica.Wallet_pay.notification.service;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.common.event.EventHandler;
import ElFabrica.Wallet_pay.notification.Notification;
import ElFabrica.Wallet_pay.transaction.event.TransferCompletedEvent;
import ElFabrica.Wallet_pay.transaction.event.TransferCompletedEvent.TransferCompletedPayload;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Turns a TransferCompleted event into one notification for the sender and one for the receiver. */
@Component
public class TransferCompletedHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedHandler.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final UserRepository userRepository;
    private final NotificationDispatcher dispatcher;
    private final JsonMapper jsonMapper;

    public TransferCompletedHandler(
            UserRepository userRepository,
            NotificationDispatcher dispatcher,
            JsonMapper jsonMapper
    ) {
        this.userRepository = userRepository;
        this.dispatcher = dispatcher;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String consumerName() {
        return "notifications";
    }

    @Override
    public String eventType() {
        return TransferCompletedEvent.TYPE;
    }

    @Override
    public void handle(DomainEvent event) {
        TransferCompletedPayload payload = jsonMapper.treeToValue(event.payload(), TransferCompletedPayload.class);
        Optional<String> senderName = userRepository.findById(payload.senderUserId())
                .map(user -> user.getName());
        Optional<String> receiverName = userRepository.findById(payload.receiverUserId())
                .map(user -> user.getName());
        if (senderName.isEmpty() || receiverName.isEmpty()) {
            log.warn("Dropping event {}: transfer {} references an unknown user", event.eventId(),
                    payload.transactionId());
            return;
        }

        String amount = formatBrl(payload.amount());
        String when = DATE_TIME.format(event.occurredAt());

        dispatcher.dispatch(new Notification(
                event.eventId(),
                payload.senderUserId(),
                Notification.TRANSFER_SENT,
                "Transferência enviada",
                "Você enviou " + amount + " para " + receiverName.get() + " em " + when + "."
        ));
        dispatcher.dispatch(new Notification(
                event.eventId(),
                payload.receiverUserId(),
                Notification.TRANSFER_RECEIVED,
                "Transferência recebida",
                "Você recebeu " + amount + " de " + senderName.get() + " em " + when + "."
        ));
    }

    private static String formatBrl(String amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + format.format(new BigDecimal(amount));
    }
}
