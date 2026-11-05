package ElFabrica.Wallet_pay.common.email;

import java.util.regex.Pattern;

public record EmailMessage(String to, String subject, String body) {

    private static final Pattern SIMPLE_ADDRESS = Pattern.compile("^[^\s@,;<>]+@[^\s@,;<>]+$");

    public EmailMessage {
        if (to == null || !SIMPLE_ADDRESS.matcher(to).matches()) {
            throw new IllegalArgumentException("Invalid e-mail recipient");
        }
        if (subject == null || subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("E-mail subject must be a single line");
        }
        if (body == null) {
            throw new IllegalArgumentException("E-mail body is required");
        }
    }
}
