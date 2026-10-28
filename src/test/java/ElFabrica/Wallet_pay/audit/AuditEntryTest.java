package ElFabrica.Wallet_pay.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

    @Test
    void shouldBuildMetadataSkippingNullsAndUsingEnumNames() {
        UUID walletId = UUID.randomUUID();

        Map<String, String> metadata = AuditEntry.metadata(
                "amount", "10.00",
                "walletId", walletId,
                "failureCode", ErrorCode.INSUFFICIENT_BALANCE,
                "description", null
        );

        assertThat(metadata).containsExactlyInAnyOrderEntriesOf(Map.of(
                "amount", "10.00",
                "walletId", walletId.toString(),
                "failureCode", "INSUFFICIENT_BALANCE"
        ));
    }

    @Test
    void shouldDeriveOutcomeFromFailureCode() {
        AuditEntry success = AuditEntry.success(AuditAction.DEPOSIT_CREATED, UUID.randomUUID(), Map.of());
        AuditEntry failure = AuditEntry.failure(AuditAction.DEPOSIT_REJECTED, null, ErrorCode.INVALID_AMOUNT, Map.of());

        assertThat(success.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(failure.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(AuditAction.DEPOSIT_REJECTED.resourceType()).isEqualTo(AuditResourceType.WALLET);
        assertThat(AuditAction.TRANSFER_CREATED.resourceType()).isEqualTo(AuditResourceType.TRANSACTION);
    }

    @Test
    void shouldTruncateRawValuesLongerThanTheLimit() {
        String exact = "1".repeat(32);

        assertThat(AuditEntry.truncateRaw(null)).isNull();
        assertThat(AuditEntry.truncateRaw(exact)).isEqualTo(exact);
        assertThat(AuditEntry.truncateRaw(exact + "9")).isEqualTo(exact + "...");
    }

    @Test
    void shouldKeepActorWhenInformed() {
        UUID userId = UUID.randomUUID();

        AuditEntry entry = AuditEntry.success(AuditAction.LOGOUT, null, null).withActor(userId);

        assertThat(entry.actorUserId()).isEqualTo(userId);
        assertThat(entry.metadata()).isEmpty();
    }
}
