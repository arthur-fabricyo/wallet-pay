package ElFabrica.Wallet_pay.notification.repository;

import ElFabrica.Wallet_pay.notification.NotificationChannelType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDeliveryRepository {

    private final JdbcClient jdbcClient;

    public NotificationDeliveryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean exists(UUID eventId, UUID userId, NotificationChannelType channel) {
        Long count = jdbcClient.sql("""
                        select count(*) from notification_deliveries
                        where event_id = :eventId and user_id = :userId and channel = :channel
                        """)
                .param("eventId", eventId)
                .param("userId", userId)
                .param("channel", channel.name())
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    public void markDelivered(UUID eventId, UUID userId, NotificationChannelType channel, Instant now) {
        jdbcClient.sql("""
                        insert into notification_deliveries (id, event_id, user_id, channel, delivered_at)
                        values (:id, :eventId, :userId, :channel, :now)
                        on conflict (event_id, user_id, channel) do nothing
                        """)
                .param("id", UUID.randomUUID())
                .param("eventId", eventId)
                .param("userId", userId)
                .param("channel", channel.name())
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }
}
