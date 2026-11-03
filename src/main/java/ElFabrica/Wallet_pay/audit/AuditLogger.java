package ElFabrica.Wallet_pay.audit;

import ElFabrica.Wallet_pay.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLogger {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditLogger(JdbcClient jdbcClient, JsonMapper jsonMapper, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        insert(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuditEntry entry) {
        insert(entry);
    }

    private void insert(AuditEntry entry) {
        UUID actorUserId = entry.actorUserId() != null ? entry.actorUserId() : CurrentActor.userId();
        AuditActorType actorType = actorUserId != null ? AuditActorType.USER : AuditActorType.ANONYMOUS;

        jdbcClient.sql("""
                        insert into audit_events (
                            id, occurred_at, actor_type, actor_user_id, action, resource_type, resource_id,
                            outcome, failure_code, metadata, ip, request_id
                        ) values (
                            :id, :occurredAt, :actorType, :actorUserId, :action, :resourceType, :resourceId,
                            :outcome, :failureCode, cast(:metadata as jsonb), :ip, :requestId
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("occurredAt", clock.instant().atOffset(ZoneOffset.UTC))
                .param("actorType", actorType.name())
                .param("actorUserId", actorUserId)
                .param("action", entry.action().name())
                .param("resourceType", entry.action().resourceType().name())
                .param("resourceId", entry.resourceId())
                .param("outcome", entry.outcome().name())
                .param("failureCode", entry.failureCode() == null ? null : entry.failureCode().name())
                .param("metadata", jsonMapper.writeValueAsString(entry.metadata()))
                .param("ip", CurrentActor.remoteAddress())
                .param("requestId", MDC.get(RequestIdFilter.MDC_KEY))
                .update();
    }
}
