package ElFabrica.Wallet_pay.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "0b9f3c1e-5a52-4c8b-9a51-1f3d2e7c0001", "chave_1-A"})
    void shouldAcceptValidKeys(String key) {
        assertThat(IdempotencyKeys.require(key)).isEqualTo(key);
    }

    @Test
    void shouldAcceptKeyWithOneHundredCharacters() {
        String key = "a".repeat(100);

        assertThat(IdempotencyKeys.require(key)).isEqualTo(key);
    }

    @ParameterizedTest
    @MethodSource("missingKeys")
    void shouldRequireKey(String key) {
        assertThatThrownBy(() -> IdempotencyKeys.require(key))
                .isInstanceOfSatisfying(IdempotencyKeyRequiredException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED))
                .hasMessage("Idempotency-Key e obrigatorio");
    }

    static Stream<String> missingKeys() {
        return Stream.of(null, "", "   ");
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void shouldRejectInvalidKey(String key) {
        assertThatThrownBy(() -> IdempotencyKeys.require(key))
                .isInstanceOfSatisfying(IdempotencyKeyInvalidException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID));
    }

    static Stream<String> invalidKeys() {
        return Stream.of("chave com espaco", "chave!", "a".repeat(101), " chave");
    }
}
