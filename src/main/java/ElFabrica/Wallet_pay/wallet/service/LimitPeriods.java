package ElFabrica.Wallet_pay.wallet.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

public final class LimitPeriods {

    public record Window(Instant start, Instant end) {
    }

    private LimitPeriods() {
    }

    public static Window day(Instant now, ZoneId zone) {
        Instant start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        return new Window(start, now);
    }

    public static Window month(Instant now, ZoneId zone) {
        Instant start = now.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant();
        return new Window(start, now);
    }

    public static Optional<Window> night(Instant now, ZoneId zone, LocalTime start, LocalTime end) {
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        LocalDate today = local.toLocalDate();
        LocalTime time = local.toLocalTime();

        if (start.isAfter(end)) {
            if (!time.isBefore(start)) {
                return Optional.of(window(today.atTime(start), today.plusDays(1).atTime(end), zone));
            }
            if (time.isBefore(end)) {
                return Optional.of(window(today.minusDays(1).atTime(start), today.atTime(end), zone));
            }
            return Optional.empty();
        }

        if (!time.isBefore(start) && time.isBefore(end)) {
            return Optional.of(window(today.atTime(start), today.atTime(end), zone));
        }
        return Optional.empty();
    }

    private static Window window(LocalDateTime start, LocalDateTime end, ZoneId zone) {
        return new Window(start.atZone(zone).toInstant(), end.atZone(zone).toInstant());
    }
}
