package ElFabrica.Wallet_pay.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private static final int SCALE = 2;
    private static final int MAX_INTEGER_DIGITS = 17;

    private Money() {
    }

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAmountException("Valor e obrigatorio");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw new InvalidAmountException("Valor invalido");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Valor deve ser maior que zero");
        }
        if (amount.scale() > SCALE) {
            throw new InvalidAmountException("Valor deve ter no maximo duas casas decimais");
        }
        if ((long) amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidAmountException("Valor invalido");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static String format(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }
}
