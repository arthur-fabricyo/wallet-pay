package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.auth.domain.RefreshTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.RefreshTokenRepository;
import ElFabrica.Wallet_pay.common.error.BusinessException;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final AuditLogger auditLogger;

    public AuthService(
            LoginUseCase loginUseCase,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            AuditLogger auditLogger
    ) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.auditLogger = auditLogger;
    }

    public AuthTokenResult login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        try {
            return loginUseCase.login(normalizedEmail, password);
        } catch (InvalidCredentialsException | EmailNotVerifiedException exception) {
            throw loginFailed(normalizedEmail, exception);
        }
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
