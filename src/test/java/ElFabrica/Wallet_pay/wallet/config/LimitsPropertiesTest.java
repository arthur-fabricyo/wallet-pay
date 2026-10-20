package ElFabrica.Wallet_pay.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LimitsPropertiesTest {

    @Test
    void shouldExposeParsedTimezoneAndNightWindow() {
        LimitsProperties properties = new LimitsProperties(
                "America/Sao_Paulo",
                new LimitsProperties.Night("20:00", "06:00"),
                transfer("5000.00", "10000.00", "50000.00", "1000.00", "1000.00"),
                new LimitsProperties.Deposit(new BigDecimal("10000.00"), new BigDecimal("20000.00"))
        );

        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
        assertThat(properties.night().startTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(properties.night().endTime()).isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    void shouldRejectIncoherentTransferLimits() {
        assertThatThrownBy(() -> transfer("5000.00", "4000.00", "50000.00", "1000.00", "1000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transferDaily");
        assertThatThrownBy(() -> transfer("5000.00", "10000.00", "50000.00", "6000.00", "7000.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transferNightPerOperation");
    }

    @Test
    void shouldRejectDepositPerOperationAboveDaily() {
        assertThatThrownBy(() -> new LimitsProperties.Deposit(new BigDecimal("30000.00"), new BigDecimal("20000.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUnknownTimezoneAndInvalidNightTime() {
        assertThatThrownBy(() -> new LimitsProperties.Night("25:00", "06:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitsProperties(
                "Lua/Base",
                new LimitsProperties.Night("20:00", "06:00"),
                transfer("5000.00", "10000.00", "50000.00", "1000.00", "1000.00"),
                new LimitsProperties.Deposit(new BigDecimal("10000.00"), new BigDecimal("20000.00"))
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static LimitsProperties.Transfer transfer(
            String perOperation,
            String daily,
            String monthly,
            String nightPerOperation,
            String nightWindow
    ) {
        return new LimitsProperties.Transfer(
                new BigDecimal(perOperation),
                new BigDecimal(daily),
                new BigDecimal(monthly),
                new BigDecimal(nightPerOperation),
                new BigDecimal(nightWindow)
        );
    }
}
