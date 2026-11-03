package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.wallet.config.LimitsProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record EffectiveTransferLimits(
        BigDecimal perOperation,
        BigDecimal daily,
        BigDecimal monthly,
        BigDecimal nightPerOperation,
        BigDecimal nightWindow
) {
    public static EffectiveTransferLimits of(
            LimitsProperties.Transfer global,
            BigDecimal customPerOperation,
            BigDecimal customDaily,
            BigDecimal customMonthly,
            BigDecimal customNightPerOperation,
            BigDecimal customNightWindow
    ) {
        return new EffectiveTransferLimits(
                min(global.perOperation(), customPerOperation),
                min(global.daily(), customDaily),
                min(global.monthly(), customMonthly),
                min(global.nightPerOperation(), customNightPerOperation),
                min(global.nightWindow(), customNightWindow)
        );
    }

    public Map<String, String> coherenceViolations() {
        Map<String, String> violations = new LinkedHashMap<>();
        if (perOperation.compareTo(daily) > 0) {
            violations.put("transferDaily", "Limite diario deve ser maior ou igual ao limite por operacao");
        }
        if (daily.compareTo(monthly) > 0) {
            violations.put("transferMonthly", "Limite mensal deve ser maior ou igual ao limite diario");
        }
        if (nightPerOperation.compareTo(nightWindow) > 0) {
            violations.put(
                    "transferNightWindow",
                    "Limite da janela noturna deve ser maior ou igual ao limite noturno por operacao"
            );
        }
        if (nightPerOperation.compareTo(perOperation) > 0) {
            violations.put(
                    "transferNightPerOperation",
                    "Limite noturno por operacao deve ser menor ou igual ao limite por operacao"
            );
        }
        return violations;
    }

    private static BigDecimal min(BigDecimal global, BigDecimal custom) {
        return custom == null ? global : global.min(custom);
    }
}
