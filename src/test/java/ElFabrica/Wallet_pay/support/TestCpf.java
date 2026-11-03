package ElFabrica.Wallet_pay.support;

import java.util.concurrent.ThreadLocalRandom;

public final class TestCpf {

    private TestCpf() {
    }

    public static String generate() {
        int[] digits = new int[11];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        do {
            for (int i = 0; i < 9; i++) {
                digits[i] = random.nextInt(10);
            }
        } while (allEqual(digits));
        digits[9] = checkDigit(digits, 9);
        digits[10] = checkDigit(digits, 10);

        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static int checkDigit(int[] digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * (length + 1 - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }

    private static boolean allEqual(int[] digits) {
        for (int i = 1; i < 9; i++) {
            if (digits[i] != digits[0]) {
                return false;
            }
        }
        return true;
    }
}
