package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import ElFabrica.Wallet_pay.auth.domain.PasswordResetTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.PasswordResetTokenRepository;
import ElFabrica.Wallet_pay.auth.repository.RefreshTokenRepository;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {

    private static final int TOKEN_BYTES = 32;
    private static final String PASSWORD_POLICY_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordRecoverySender passwordRecoverySender;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;
    private final SecureRandom secureRandom;
    private final Duration tokenTtl;

    @Autowired
    public PasswordRecoveryService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordRecoverySender passwordRecoverySender,
            PasswordEncoder passwordEncoder,
            AuditLogger auditLogger,
            @Value("${wallet-pay.password-recovery.token-ttl-minutes:30}") long tokenTtlMinutes
    ) {
        this(
                tokenRepository,
                userRepository,
                refreshTokenRepository,
                passwordRecoverySender,
                passwordEncoder,
                auditLogger,
                new SecureRandom(),
                Duration.ofMinutes(tokenTtlMinutes)
        );
    }

    PasswordRecoveryService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordRecoverySender passwordRecoverySender,
            PasswordEncoder passwordEncoder,
            AuditLogger auditLogger,
            SecureRandom secureRandom,
            Duration tokenTtl
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordRecoverySender = passwordRecoverySender;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.secureRandom = secureRandom;
        this.tokenTtl = tokenTtl;
    }

    @Transactional
    public void request(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        userRepository.findByEmail(normalizedEmail)
                .ifPresent(this::issueFor);
    }

    @Transactional
    public void reset(String tokenValue, String newPassword) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw invalidToken("Token de redefinicao de senha invalido");
        }

        PasswordResetTokenEntity token = tokenRepository.findByToken(tokenValue.trim())
                .orElseThrow(() -> invalidToken("Token de redefinicao de senha invalido"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new PasswordResetTokenException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED, "Token de redefinicao de senha expirado");
        }

        if (token.isUsed()) {
            throw new PasswordResetTokenException(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED, "Token de redefinicao de senha ja utilizado");
        }

        validatePassword(newPassword);

        UserEntity user = token.getUser();
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        refreshTokenRepository.invalidateActiveTokensByUser(user, now);
        auditLogger.record(AuditEntry.success(AuditAction.PASSWORD_RESET_COMPLETED, user.getId(), Map.of())
                .withActor(user.getId()));
    }

    private void issueFor(UserEntity user) {
        Instant now = Instant.now();
        tokenRepository.markPendingTokensAsUsed(user, now);
        PasswordResetTokenEntity token = tokenRepository.save(new PasswordResetTokenEntity(
                user,
                generateToken(),
                now.plus(tokenTtl)
        ));
        passwordRecoverySender.sendPasswordRecovery(user.getEmail(), token.getToken());
        auditLogger.record(AuditEntry.success(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), Map.of()));
    }

    private void validatePassword(String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || !newPassword.matches(PASSWORD_POLICY_PATTERN)) {
            throw new InvalidPasswordPolicyException("Senha deve ter pelo menos 8 caracteres, uma letra e um numero");
        }
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private PasswordResetTokenException invalidToken(String message) {
        return new PasswordResetTokenException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, message);
    }
}
