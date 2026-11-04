package ElFabrica.Wallet_pay.notification.repository;

import ElFabrica.Wallet_pay.notification.NotificationChannelType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationPreferenceRepository {

    private final JdbcClient jdbcClient;

    public NotificationPreferenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Explicit choices only; channels without a row are absent (the caller applies the channel default). */
    public Map<NotificationChannelType, Boolean> findByUserId(UUID userId) {
        Map<NotificationChannelType, Boolean> result = new EnumMap<>(NotificationChannelType.class);
        jdbcClient.sql("select channel, enabled from notification_preferences where user_id = :userId")
                .param("userId", userId)
                .query((rs, row) -> {
                    result.put(NotificationChannelType.valueOf(rs.getString("channel")), rs.getBoolean("enabled"));
                    return row;
                })
                .list();
        return result;
    }

    public void upsert(UUID userId, NotificationChannelType channel, boolean enabled, Instant now) {
        jdbcClient.sql("""
                        insert into notification_preferences (id, user_id, channel, enabled, created_at, updated_at)
                        values (:id, :userId, :channel, :enabled, :now, :now)
                        on conflict (user_id, channel)
                        do update set enabled = excluded.enabled, updated_at = excluded.updated_at
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("channel", channel.name())
                .param("enabled", enabled)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }
}
