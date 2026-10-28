package ElFabrica.Wallet_pay.wallet.config;

import ElFabrica.Wallet_pay.wallet.service.EffectiveTransferLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "walletpay.limits")
public record LimitsProperties(
        @NotBlank String timezone,
        @NotNull @Valid Night night,
        @NotNull @Valid Transfer transfer,
        @NotNull @Valid Deposit deposit
) {
    public LimitsProperties {
        if (timezone != null) {
            try {
                ZoneId.of(timezone);
            } catch (DateTimeException exception) {
                throw new IllegalArgumentException("walletpay.limits.timezone invalido: " + timezone, exception);
            }
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public record Night(@NotBlank String start, @NotBlank String end) {

        public Night {
            requireTime(start, "start");
            requireTime(end, "end");
            if (start != null && end != null && LocalTime.parse(start).equals(LocalTime.parse(end))) {
                throw new IllegalArgumentException("walletpay.limits.night.start e end devem ser diferentes");
            }
        }

        public LocalTime startTime() {
            return LocalTime.parse(start);
        }

        public LocalTime endTime() {
            return LocalTime.parse(end);
        }

        private static void requireTime(String value, String field) {
            if (value == null) {
                return;
            }
            try {
                LocalTime.parse(value);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "walletpay.limits.night." + field + " deve estar no formato HH:mm", exception
                );
            }
        }
    }

    public record Transfer(
            @NotNull @Positive BigDecimal perOperation,
            @NotNull @Positive BigDecimal daily,
            @NotNull @Positive BigDecimal monthly,
            @NotNull @Positive BigDecimal nightPerOperation,
            @NotNull @Positive BigDecimal nightWindow
    ) {
        public Transfer {
            requireCents(perOperation, "transfer.per-operation");
            requireCents(daily, "transfer.daily");
            requireCents(monthly, "transfer.monthly");
            requireCents(nightPerOperation, "transfer.night-per-operation");
            requireCents(nightWindow, "transfer.night-window");
            if (perOperation != null && daily != null && monthly != null
                    && nightPerOperation != null && nightWindow != null) {
                Map<String, String> violations = new EffectiveTransferLimits(
                        perOperation, daily, monthly, nightPerOperation, nightWindow
                ).coherenceViolations();
                if (!violations.isEmpty()) {
                    throw new IllegalArgumentException("walletpay.limits.transfer incoerente: " + violations);
                }
            }
        }
    }

    public record Deposit(
            @NotNull @Positive BigDecimal perOperation,
            @NotNull @Positive BigDecimal daily
    ) {
        public Deposit {
            requireCents(perOperation, "deposit.per-operation");
            requireCents(daily, "deposit.daily");
            if (perOperation != null && daily != null && perOperation.compareTo(daily) > 0) {
                throw new IllegalArgumentException(
                        "walletpay.limits.deposit: per-operation deve ser menor ou igual a daily"
                );
            }
        }
    }

    private static void requireCents(BigDecimal value, String field) {
        if (value != null && value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("walletpay.limits." + field + " aceita no maximo 2 casas decimais");
        }
    }
}
