package ElFabrica.Wallet_pay.common.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IdempotencyService {

    static final Duration TTL = Duration.ofHours(24);
    private static final int CREATED = 201;

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public IdempotencyService(JdbcClient jdbcClient, JsonMapper jsonMapper, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotentResult<T> execute(IdempotencyRequest request, Class<T> resultType, Supplier<T> action) {
        Instant now = clock.instant();
        deleteIfExpired(request, now);

        if (claim(request, now)) {
            T result = action.get();
            storeResponse(request, result);
            return new IdempotentResult<>(result, false);
        }

        StoredResponse stored = load(request);
        if (!stored.requestHash().equals(request.requestHash())) {
            throw new IdempotencyKeyReusedException("Idempotency-Key ja utilizada com outro payload");
        }
        return new IdempotentResult<>(jsonMapper.readValue(stored.responseBody(), resultType), true);
    }

    private void deleteIfExpired(IdempotencyRequest request, Instant now) {
        jdbcClient.sql("""
                        delete from idempotency_keys
                        where user_id = :userId
                          and operation = :operation
                          and idempotency_key = :key
                          and expires_at < :now
                        """)
                .param("userId", request.userId())
                .param("operation", request.operation().name())
                .param("key", request.key())
                .param("now", utc(now))
                .update();
    }

    private boolean claim(IdempotencyRequest request, Instant now) {
        int inserted = jdbcClient.sql("""
                        insert into idempotency_keys (
                            id, user_id, idempotency_key, operation, request_hash, created_at, expires_at
                        ) values (
                            :id, :userId, :key, :operation, :requestHash, :createdAt, :expiresAt
                        )
                        on conflict (user_id, operation, idempotency_key) do nothing
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", request.userId())
                .param("key", request.key())
                .param("operation", request.operation().name())
                .param("requestHash", request.requestHash())
                .param("createdAt", utc(now))
                .param("expiresAt", utc(now.plus(TTL)))
                .update();
        return inserted == 1;
    }

    private void storeResponse(IdempotencyRequest request, Object result) {
        jdbcClient.sql("""
                        update idempotency_keys
                        set response_status = :status,
                            response_body = cast(:body as jsonb)
                        where user_id = :userId
                          and operation = :operation
                          and idempotency_key = :key
                        """)
                .param("status", CREATED)
                .param("body", jsonMapper.writeValueAsString(result))
                .param("userId", request.userId())
                .param("operation", request.operation().name())
                .param("key", request.key())
                .update();
    }

    private StoredResponse load(IdempotencyRequest request) {
        return jdbcClient.sql("""
                        select request_hash, response_body::text as response_body
                        from idempotency_keys
                        where user_id = :userId
                          and operation = :operation
                          and idempotency_key = :key
                        """)
                .param("userId", request.userId())
                .param("operation", request.operation().name())
                .param("key", request.key())
                .query((resultSet, rowNumber) -> new StoredResponse(
                        resultSet.getString("request_hash"),
                        resultSet.getString("response_body")
                ))
                .single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record StoredResponse(String requestHash, String responseBody) {
    }
}
