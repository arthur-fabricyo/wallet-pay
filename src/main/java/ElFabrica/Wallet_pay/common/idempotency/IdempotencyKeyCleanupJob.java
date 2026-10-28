package ElFabrica.Wallet_pay.common.idempotency;

import java.time.Clock;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyCleanupJob {

    static final int BATCH_SIZE = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public IdempotencyKeyCleanupJob(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public int purgeExpiredKeys() {
        int total = 0;
        int deleted;
        do {
            deleted = jdbcClient.sql("""
                            delete from idempotency_keys
                            where id in (
                                select id
                                from idempotency_keys
                                where expires_at < :now
                                order by expires_at
                                limit :limit
                            )
                            """)
                    .param("now", clock.instant().atOffset(ZoneOffset.UTC))
                    .param("limit", BATCH_SIZE)
                    .update();
            total += deleted;
        } while (deleted == BATCH_SIZE);

        LOGGER.info("Chaves de idempotencia expiradas removidas: {}", total);
        return total;
    }
}
