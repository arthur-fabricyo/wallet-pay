package ElFabrica.Wallet_pay.support;

import java.util.UUID;

public record TestUser(
        UUID userId,
        UUID walletId,
        String email,
        String accessToken
) {
}
