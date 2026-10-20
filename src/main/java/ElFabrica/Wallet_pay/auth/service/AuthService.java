package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.auth.domain.RefreshTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.RefreshTokenRepository;
import ElFabrica.Wallet_pay.common.error.BusinessException;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final AuditLogger auditLogger;

    public AuthService(
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
    public AuthTokenResult login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw loginFailed(normalizedEmail, new InvalidCredentialsException("Credenciais invalidas"));
        }

        if (!user.isEmailVerified()) {
            throw loginFailed(normalizedEmail, new EmailNotVerifiedException("E-mail ainda nao verificado"));
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

    @Transactional(readOnly = true)
    public AccessTokenResult refresh(String token) {
        RefreshTokenEntity refreshToken = findValidRefreshToken(token);
        return accessTokenService.issue(refreshToken.getUser());
    }

    @Transactional
    public void logout(String token) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token invalido"));
        refreshToken.invalidate(Instant.now());
        auditLogger.record(AuditEntry.success(AuditAction.LOGOUT, null, Map.of())
                .withActor(refreshToken.getUser().getId()));
    }

    private BusinessException loginFailed(String email, BusinessException exception) {
        auditLogger.recordFailure(AuditEntry.failure(
                AuditAction.LOGIN_FAILED,
                null,
                exception.getErrorCode(),
                AuditEntry.metadata("email", email, "reason", exception.getErrorCode())
        ));
        return exception;
    }

    private RefreshTokenEntity findValidRefreshToken(String token) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token invalido"));

        Instant now = Instant.now();
        if (refreshToken.isInvalidated() || refreshToken.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token invalido");
        }

        return refreshToken;
    }
}
