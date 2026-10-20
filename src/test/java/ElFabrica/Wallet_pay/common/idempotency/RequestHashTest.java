package ElFabrica.Wallet_pay.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestHashTest {

    @Test
    void shouldProduceSha256HexOfTheJoinedFields() {
        assertThat(RequestHash.of("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldBeStableAndSensitiveToEveryField() {
        String hash = RequestHash.of("receiver", "10.00", "Almoco");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(RequestHash.of("receiver", "10.00", "Almoco")).isEqualTo(hash);
        assertThat(RequestHash.of("receiver", "10.01", "Almoco")).isNotEqualTo(hash);
        assertThat(RequestHash.of("10.00", "receiver", "Almoco")).isNotEqualTo(hash);
    }

    @Test
    void shouldTreatNullAsEmptyString() {
        assertThat(RequestHash.of("10.00", null)).isEqualTo(RequestHash.of("10.00", ""));
    }
}
