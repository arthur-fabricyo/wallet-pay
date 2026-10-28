package ElFabrica.Wallet_pay.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class TestJwt {

    public static final String SECRET = "wallet-pay-test-secret-with-32-bytes";

    private TestJwt() {
    }

    public static String bearer(UUID userId) {
        return "Bearer " + token(userId, Instant.now().plusSeconds(60));
    }

    public static String token(UUID userId, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issueTime(Date.from(expiresAt.minusSeconds(60)))
                    .expirationTime(Date.from(expiresAt))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(SECRET));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
