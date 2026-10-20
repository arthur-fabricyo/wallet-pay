package ElFabrica.Wallet_pay.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LimitPeriodsTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime NIGHT_START = LocalTime.of(20, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    @Test
    void dayStartsAtLocalMidnightEvenWhenTheUtcDateIsAhead() {
        Instant now = Instant.parse("2026-10-15T01:00:00Z");

        LimitPeriods.Window day = LimitPeriods.day(now, SAO_PAULO);

        assertThat(day.start()).isEqualTo(Instant.parse("2026-10-14T03:00:00Z"));
        assertThat(day.end()).isEqualTo(now);
    }

    @Test
    void monthStartsOnTheLocalFirstDay() {
        Instant now = Instant.parse("2026-11-01T01:00:00Z");

        assertThat(LimitPeriods.month(now, SAO_PAULO).start()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-15T20:00", "2026-10-16T05:59", "2026-10-15T23:00", "2026-10-16T02:00"})
    void nightWindowIsActive(String localDateTime) {
        assertThat(LimitPeriods.night(saoPaulo(localDateTime), SAO_PAULO, NIGHT_START, NIGHT_END)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-15T06:00", "2026-10-15T19:59", "2026-10-15T12:00"})
    void nightWindowIsInactive(String localDateTime) {
        assertThat(LimitPeriods.night(saoPaulo(localDateTime), SAO_PAULO, NIGHT_START, NIGHT_END)).isEmpty();
    }

    @Test
    void windowAtElevenPmRunsUntilTomorrowMorning() {
        Optional<LimitPeriods.Window> window =
                LimitPeriods.night(saoPaulo("2026-10-15T23:00"), SAO_PAULO, NIGHT_START, NIGHT_END);

        assertThat(window).contains(new LimitPeriods.Window(saoPaulo("2026-10-15T20:00"), saoPaulo("2026-10-16T06:00")));
    }

    @Test
    void windowAtTwoAmStartedYesterdayEvening() {
        Optional<LimitPeriods.Window> window =
                LimitPeriods.night(saoPaulo("2026-10-16T02:00"), SAO_PAULO, NIGHT_START, NIGHT_END);

        assertThat(window).contains(new LimitPeriods.Window(saoPaulo("2026-10-15T20:00"), saoPaulo("2026-10-16T06:00")));
    }

    @Test
    void supportsWindowThatDoesNotCrossMidnight() {
        LocalTime start = LocalTime.of(1, 0);
        LocalTime end = LocalTime.of(5, 0);

        assertThat(LimitPeriods.night(saoPaulo("2026-10-15T03:00"), SAO_PAULO, start, end)).isPresent();
        assertThat(LimitPeriods.night(saoPaulo("2026-10-15T05:00"), SAO_PAULO, start, end)).isEmpty();
    }

    private static Instant saoPaulo(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SAO_PAULO).toInstant();
    }
}
