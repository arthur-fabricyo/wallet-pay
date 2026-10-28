package ElFabrica.Wallet_pay.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "10, 10.00",
            "10.5, 10.50",
            "' 25.50 ', 25.50",
            "0.01, 0.01",
            "99999999999999999.99, 99999999999999999.99"
    })
    void shouldParseValidAmountsWithScaleTwo(String raw, String expected) {
        BigDecimal amount = Money.parse(raw);

        assertThat(amount).isEqualByComparingTo(expected);
        assertThat(amount.scale()).isEqualTo(2);
    }

    @Test
    void shouldRejectMissingAmount() {
        assertThatThrownBy(() -> Money.parse(null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor e obrigatorio");
        assertThatThrownBy(() -> Money.parse("   "))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor e obrigatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "10,50", "1e999999999999"})
    void shouldRejectNonNumericAmount(String raw) {
        assertThatThrownBy(() -> Money.parse(raw))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor invalido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void shouldRejectAmountNotGreaterThanZero(String raw) {
        assertThatThrownBy(() -> Money.parse(raw))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor deve ser maior que zero");
    }

    @Test
    void shouldRejectMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> Money.parse("10.123"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor deve ter no maximo duas casas decimais");
    }

    @ParameterizedTest
    @ValueSource(strings = {"100000000000000000", "100000000000000000000", "1E+30", "1E+2147483646", "1E+2147483647"})
    void shouldRejectAmountsThatDoNotFitTheDatabaseColumn(String raw) {
        assertThatThrownBy(() -> Money.parse(raw))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Valor invalido");
    }

    @Test
    void shouldUseInvalidAmountCode() {
        assertThat(new InvalidAmountException("x").getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void shouldFormatWithTwoDecimalPlaces() {
        assertThat(Money.format(new BigDecimal("10"))).isEqualTo("10.00");
        assertThat(Money.format(new BigDecimal("0.5"))).isEqualTo("0.50");
        assertThat(Money.format(new BigDecimal("1E+3"))).isEqualTo("1000.00");
    }
}
