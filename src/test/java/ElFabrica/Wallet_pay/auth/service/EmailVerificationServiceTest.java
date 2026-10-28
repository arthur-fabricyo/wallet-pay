package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.auth.domain.EmailVerificationTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.EmailVerificationTokenRepository;
import ElFabrica.Wallet_pay.support.RecordingAuditLogger;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailVerificationServiceTest {

    @Test
    void shouldIssueTokenForUnverifiedUser() {
        UserEntity user = unverifiedUser();
        FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository(null);
        EmailVerificationService service = service(tokenRepository, new FakeUserRepository(user));

        service.issueFor(user);

        assertThat(tokenRepository.invalidatedUser).isSameAs(user);
        assertThat(tokenRepository.savedToken.getUser()).isSameAs(user);
        assertThat(tokenRepository.savedToken.getToken()).isNotBlank();
        assertThat(tokenRepository.sender.email).isEqualTo("joao@email.com");
        assertThat(tokenRepository.sender.token).isEqualTo(tokenRepository.savedToken.getToken());
    }

    @Test
    void shouldNotIssueTokenForAlreadyVerifiedUser() {
        UserEntity user = unverifiedUser();
        setField(user, "emailVerified", true);
        FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository(null);
        EmailVerificationService service = service(tokenRepository, new FakeUserRepository(user));

        service.issueFor(user);

        assertThat(tokenRepository.invalidatedUser).isNull();
        assertThat(tokenRepository.savedToken).isNull();
        assertThat(tokenRepository.sender.email).isNull();
    }

    @Test
    void shouldConfirmValidTokenAndVerifyUser() {
        UserEntity user = unverifiedUser();
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity(
                user,
                "valid-token",
                Instant.now().plusSeconds(60)
        );
        EmailVerificationService service = service(
                new FakeEmailVerificationTokenRepository(token),
                new FakeUserRepository(user)
        );

        service.confirm("valid-token");

        assertThat(token.isUsed()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void shouldRejectInvalidToken() {
        EmailVerificationService service = service(
                new FakeEmailVerificationTokenRepository(null),
                new FakeUserRepository(null)
        );

        assertThatThrownBy(() -> service.confirm("missing-token"))
                .isInstanceOfSatisfying(EmailVerificationTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));
    }

    @Test
    void shouldRejectBlankTokenAsInvalidToken() {
        EmailVerificationService service = service(
                new FakeEmailVerificationTokenRepository(null),
                new FakeUserRepository(null)
        );

        assertThatThrownBy(() -> service.confirm(" "))
                .isInstanceOfSatisfying(EmailVerificationTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));
    }

    @Test
    void shouldRejectExpiredToken() {
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity(
                unverifiedUser(),
                "expired-token",
                Instant.now().minusSeconds(1)
        );
        EmailVerificationService service = service(
                new FakeEmailVerificationTokenRepository(token),
                new FakeUserRepository(null)
        );

        assertThatThrownBy(() -> service.confirm("expired-token"))
                .isInstanceOfSatisfying(EmailVerificationTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED));
    }

    @Test
    void shouldRejectAlreadyUsedToken() {
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity(
                unverifiedUser(),
                "used-token",
                Instant.now().plusSeconds(60)
        );
        token.markUsed(Instant.now());
        EmailVerificationService service = service(
                new FakeEmailVerificationTokenRepository(token),
                new FakeUserRepository(null)
        );

        assertThatThrownBy(() -> service.confirm("used-token"))
                .isInstanceOfSatisfying(EmailVerificationTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_TOKEN_ALREADY_USED));
    }

    @Test
    void shouldResendTokenWithoutRevealingMissingUser() {
        FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository(null);
        EmailVerificationService service = service(tokenRepository, new FakeUserRepository(null));

        service.resend("missing@email.com");

        assertThat(tokenRepository.savedToken).isNull();
    }

    @Test
    void shouldInvalidatePendingTokensWhenResending() {
        UserEntity user = unverifiedUser();
        FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository(null);
        EmailVerificationService service = service(tokenRepository, new FakeUserRepository(user));

        service.resend("JOAO@EMAIL.COM");

        assertThat(tokenRepository.invalidatedUser).isSameAs(user);
        assertThat(tokenRepository.savedToken).isNotNull();
    }

    private static EmailVerificationService service(
            FakeEmailVerificationTokenRepository tokenRepository,
            FakeUserRepository userRepository
    ) {
        return new EmailVerificationService(
                tokenRepository.proxy(),
                userRepository.proxy(),
                tokenRepository.sender,
                new RecordingAuditLogger(),
                new SecureRandom(),
                Duration.ofHours(24)
        );
    }

    private static UserEntity unverifiedUser() {
        UserEntity user = new UserEntity("Joao Silva", "joao@email.com", "hash", "52998224725");
        setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeEmailVerificationTokenRepository {

        private final EmailVerificationTokenEntity existingToken;
        private final RecordingEmailVerificationSender sender = new RecordingEmailVerificationSender();
        private EmailVerificationTokenEntity savedToken;
        private UserEntity invalidatedUser;

        private FakeEmailVerificationTokenRepository(EmailVerificationTokenEntity existingToken) {
            this.existingToken = existingToken;
        }

        private EmailVerificationTokenRepository proxy() {
            return (EmailVerificationTokenRepository) Proxy.newProxyInstance(
                    EmailVerificationTokenRepository.class.getClassLoader(),
                    new Class<?>[] {EmailVerificationTokenRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByToken" -> Optional.ofNullable(existingToken)
                                .filter(token -> token.getToken().equals(args[0]));
                        case "markPendingTokensAsUsed" -> {
                            invalidatedUser = (UserEntity) args[0];
                            yield null;
                        }
                        case "save" -> {
                            savedToken = (EmailVerificationTokenEntity) args[0];
                            yield savedToken;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class RecordingEmailVerificationSender implements EmailVerificationSender {

        private String email;
        private String token;

        @Override
        public void sendVerification(String email, String token) {
            this.email = email;
            this.token = token;
        }
    }

    private static final class FakeUserRepository {

        private final UserEntity user;

        private FakeUserRepository(UserEntity user) {
            this.user = user;
        }

        private UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[] {UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByEmail" -> Optional.ofNullable(user)
                                .filter(existingUser -> existingUser.getEmail().equals(args[0]));
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
