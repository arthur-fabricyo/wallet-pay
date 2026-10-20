package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.auth.service.PasswordRecoverySender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CapturingPasswordRecoverySender implements PasswordRecoverySender {

    private final Map<String, String> lastTokenByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendPasswordRecovery(String email, String token) {
        lastTokenByEmail.put(email, token);
    }

    public String lastTokenFor(String email) {
        return lastTokenByEmail.get(email);
    }
}
