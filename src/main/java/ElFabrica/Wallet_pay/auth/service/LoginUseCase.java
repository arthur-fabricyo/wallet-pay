package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.auth.domain.RefreshTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.RefreshTokenRepository;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional part of login. Failures are recorded by {@link AuthService}, outside this transaction,
 * so the REQUIRES_NEW failure audit never needs a second connection while this one is held.
 */
@Service
public class LoginUseCase {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final AuditLogger auditLogger;

    public LoginUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            AuditLogger auditLogger
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public AuthTokenResult login(String normalizedEmail, String password) {
        UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciais invalidas");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("E-mail ainda nao verificado");
        }

        AccessTokenResult accessToken = accessTokenService.issue(user);
        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                UUID.randomUUID().toString(),
                user,
                Instant.now().plus(REFRESH_TOKEN_TTL)
        );
        refreshTokenRepository.save(refreshToken);
        auditLogger.record(AuditEntry.success(AuditAction.LOGIN_SUCCEEDED, null, Map.of()).withActor(user.getId()));

        return new AuthTokenResult(
                accessToken.accessToken(),
                accessToken.tokenType(),
                accessToken.expiresIn(),
                refreshToken.getToken()
        );
    }
}
