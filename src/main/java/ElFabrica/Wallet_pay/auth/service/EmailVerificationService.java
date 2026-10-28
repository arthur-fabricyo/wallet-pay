package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import ElFabrica.Wallet_pay.auth.domain.EmailVerificationTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.EmailVerificationTokenRepository;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService implements EmailVerificationTokenIssuer {

    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationSender emailVerificationSender;
    private final AuditLogger auditLogger;
    private final SecureRandom secureRandom;
    private final Duration tokenTtl;

    @Autowired
    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailVerificationSender emailVerificationSender,
            AuditLogger auditLogger,
            @Value("${wallet-pay.email-verification.token-ttl-hours:24}") long tokenTtlHours
    ) {
        this(
                tokenRepository,
                userRepository,
                emailVerificationSender,
                auditLogger,
                new SecureRandom(),
                Duration.ofHours(tokenTtlHours)
        );
    }

    EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailVerificationSender emailVerificationSender,
            AuditLogger auditLogger,
            SecureRandom secureRandom,
            Duration tokenTtl
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailVerificationSender = emailVerificationSender;
        this.auditLogger = auditLogger;
        this.secureRandom = secureRandom;
        this.tokenTtl = tokenTtl;
    }

    @Override
    @Transactional
    public void issueFor(UserEntity user) {
        if (user.isEmailVerified()) {
            return;
        }

        Instant now = Instant.now();
        tokenRepository.markPendingTokensAsUsed(user, now);
        EmailVerificationTokenEntity token = tokenRepository.save(new EmailVerificationTokenEntity(
                user,
                generateToken(),
                now.plus(tokenTtl)
        ));
        emailVerificationSender.sendVerification(user.getEmail(), token.getToken());
    }

    @Transactional
    public void resend(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        userRepository.findByEmail(normalizedEmail)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueFor);
    }

    @Transactional
    public void confirm(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw invalidToken("Token de verificacao invalido");
        }

        EmailVerificationTokenEntity token = tokenRepository.findByToken(tokenValue.trim())
                .orElseThrow(() -> invalidToken("Token de verificacao invalido"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new EmailVerificationTokenException(ErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED, "Token de verificacao expirado");
        }

        if (token.isUsed()) {
            throw new EmailVerificationTokenException(ErrorCode.EMAIL_VERIFICATION_TOKEN_ALREADY_USED, "Token de verificacao ja utilizado");
        }

        token.markUsed(now);
        UserEntity user = token.getUser();
        user.markEmailVerified();
        auditLogger.record(AuditEntry.success(AuditAction.EMAIL_VERIFIED, user.getId(), Map.of())
                .withActor(user.getId()));
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private EmailVerificationTokenException invalidToken(String message) {
        return new EmailVerificationTokenException(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID, message);
    }
}
