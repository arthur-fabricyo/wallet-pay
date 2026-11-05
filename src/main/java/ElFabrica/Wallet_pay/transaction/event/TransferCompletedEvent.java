package ElFabrica.Wallet_pay.transaction.event;

import ElFabrica.Wallet_pay.common.event.DomainEvent;
import ElFabrica.Wallet_pay.common.money.Money;
import ElFabrica.Wallet_pay.transaction.domain.TransactionEntity;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

public final class TransferCompletedEvent {

    public static final String TYPE = "TransferCompleted";

    public record TransferCompletedPayload(
            UUID transactionId,
            String amount,
            String currency,
            UUID senderUserId,
            UUID receiverUserId
    ) {
    }

    private TransferCompletedEvent() {
    }

    public static DomainEvent of(
            TransactionEntity transaction,
            UUID senderUserId,
            UUID receiverUserId,
            JsonMapper mapper,
            Instant occurredAt
    ) {
        TransferCompletedPayload payload = new TransferCompletedPayload(
                transaction.getId(),
                Money.format(transaction.getAmount()),
                transaction.getCurrency(),
                senderUserId,
                receiverUserId
        );
        return new DomainEvent(
                UUID.randomUUID(), TYPE, occurredAt, transaction.getId(), null, mapper.valueToTree(payload));
    }
}
