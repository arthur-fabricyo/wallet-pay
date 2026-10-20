package ElFabrica.Wallet_pay.auth.service;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.auth.domain.PasswordResetTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.PasswordResetTokenRepository;
import ElFabrica.Wallet_pay.auth.repository.RefreshTokenRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordRecoveryServiceTest {

    @Test
    void shouldRequestRecoveryForExistingUserWithNormalizedEmail() {
        UserEntity user = user();
        FakePasswordResetTokenRepository tokenRepository = new FakePasswordResetTokenRepository(null);
        FakeUserRepository userRepository = new FakeUserRepository(user);
        PasswordRecoveryService service = service(tokenRepository, userRepository, new FakeRefreshTokenRepository());

        service.request("JOAO@EMAIL.COM");

        assertThat(userRepository.emailSearched).isEqualTo("joao@email.com");
        assertThat(tokenRepository.invalidatedUser).isSameAs(user);
        assertThat(tokenRepository.savedToken.getUser()).isSameAs(user);
        assertThat(tokenRepository.savedToken.getToken()).isNotBlank();
        assertThat(tokenRepository.sender.email).isEqualTo("joao@email.com");
        assertThat(tokenRepository.sender.token).isEqualTo(tokenRepository.savedToken.getToken());
    }

    @Test
    void shouldReturnWithoutRevealingMissingUserOnRequest() {
        FakePasswordResetTokenRepository tokenRepository = new FakePasswordResetTokenRepository(null);
        PasswordRecoveryService service = service(
                tokenRepository,
                new FakeUserRepository(null),
                new FakeRefreshTokenRepository()
        );

        service.request("missing@email.com");

        assertThat(tokenRepository.savedToken).isNull();
    }

    @Test
    void shouldResetPasswordWithValidTokenAndInvalidateRefreshTokens() {
        UserEntity user = user();
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                user,
                "valid-token",
                Instant.now().plusSeconds(60)
        );
        FakeRefreshTokenRepository refreshTokenRepository = new FakeRefreshTokenRepository();
        PasswordRecoveryService service = service(
                new FakePasswordResetTokenRepository(token),
                new FakeUserRepository(user),
                refreshTokenRepository
        );

        service.reset("valid-token", "NovaSenha123");

        assertThat(token.isUsed()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("encoded-NovaSenha123");
        assertThat(user.getPasswordHash()).isNotEqualTo("old-hash");
        assertThat(refreshTokenRepository.invalidatedUser).isSameAs(user);
    }

    @Test
    void shouldRejectInvalidToken() {
        PasswordRecoveryService service = service(
                new FakePasswordResetTokenRepository(null),
                new FakeUserRepository(null),
                new FakeRefreshTokenRepository()
        );

        assertThatThrownBy(() -> service.reset("missing-token", "NovaSenha123"))
                .isInstanceOfSatisfying(PasswordResetTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    @Test
    void shouldRejectBlankTokenAsInvalidToken() {
        PasswordRecoveryService service = service(
                new FakePasswordResetTokenRepository(null),
                new FakeUserRepository(null),
                new FakeRefreshTokenRepository()
        );

        assertThatThrownBy(() -> service.reset(" ", "NovaSenha123"))
                .isInstanceOfSatisfying(PasswordResetTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    @Test
    void shouldRejectExpiredToken() {
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                user(),
                "expired-token",
                Instant.now().minusSeconds(1)
        );
        PasswordRecoveryService service = service(
                new FakePasswordResetTokenRepository(token),
                new FakeUserRepository(null),
                new FakeRefreshTokenRepository()
        );

        assertThatThrownBy(() -> service.reset("expired-token", "NovaSenha123"))
                .isInstanceOfSatisfying(PasswordResetTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED));
    }

    @Test
    void shouldRejectAlreadyUsedToken() {
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                user(),
                "used-token",
                Instant.now().plusSeconds(60)
        );
        token.markUsed(Instant.now());
        PasswordRecoveryService service = service(
                new FakePasswordResetTokenRepository(token),
                new FakeUserRepository(null),
                new FakeRefreshTokenRepository()
        );

        assertThatThrownBy(() -> service.reset("used-token", "NovaSenha123"))
                .isInstanceOfSatisfying(PasswordResetTokenException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED));
    }

    @Test
    void shouldRejectPasswordOutsidePolicy() {
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(
                user(),
                "valid-token",
                Instant.now().plusSeconds(60)
        );
        PasswordRecoveryService service = service(
                new FakePasswordResetTokenRepository(token),
                new FakeUserRepository(null),
                new FakeRefreshTokenRepository()
        );

        assertThatThrownBy(() -> service.reset("valid-token", "somenteletras"))
                .isInstanceOf(InvalidPasswordPolicyException.class);
        assertThat(token.isUsed()).isFalse();
    }

    private static PasswordRecoveryService service(
            FakePasswordResetTokenRepository tokenRepository,
            FakeUserRepository userRepository,
            FakeRefreshTokenRepository refreshTokenRepository
    ) {
        return new PasswordRecoveryService(
                tokenRepository.proxy(),
                userRepository.proxy(),
                refreshTokenRepository.proxy(),
                tokenRepository.sender,
                passwordEncoder(),
                new RecordingAuditLogger(),
                new SecureRandom(),
                Duration.ofMinutes(30)
        );
    }

    private static PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded-" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        };
    }

    private static UserEntity user() {
        UserEntity user = new UserEntity("Joao Silva", "joao@email.com", "old-hash", "52998224725");
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

    private static final class FakePasswordResetTokenRepository {

        private final PasswordResetTokenEntity existingToken;
        private final RecordingPasswordRecoverySender sender = new RecordingPasswordRecoverySender();
        private PasswordResetTokenEntity savedToken;
        private UserEntity invalidatedUser;

        private FakePasswordResetTokenRepository(PasswordResetTokenEntity existingToken) {
            this.existingToken = existingToken;
        }

        private PasswordResetTokenRepository proxy() {
            return (PasswordResetTokenRepository) Proxy.newProxyInstance(
                    PasswordResetTokenRepository.class.getClassLoader(),
                    new Class<?>[] {PasswordResetTokenRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByToken" -> Optional.ofNullable(existingToken)
                                .filter(token -> token.getToken().equals(args[0]));
                        case "markPendingTokensAsUsed" -> {
                            invalidatedUser = (UserEntity) args[0];
                            yield null;
                        }
                        case "save" -> {
                            savedToken = (PasswordResetTokenEntity) args[0];
                            yield savedToken;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeUserRepository {

        private final UserEntity user;
        private String emailSearched;

        private FakeUserRepository(UserEntity user) {
            this.user = user;
        }

        private UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[] {UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByEmail" -> {
                            emailSearched = (String) args[0];
                            yield Optional.ofNullable(user)
                                    .filter(existingUser -> existingUser.getEmail().equals(args[0]));
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeRefreshTokenRepository {

        private UserEntity invalidatedUser;

        private RefreshTokenRepository proxy() {
            return (RefreshTokenRepository) Proxy.newProxyInstance(
                    RefreshTokenRepository.class.getClassLoader(),
                    new Class<?>[] {RefreshTokenRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "invalidateActiveTokensByUser" -> {
                            invalidatedUser = (UserEntity) args[0];
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class RecordingPasswordRecoverySender implements PasswordRecoverySender {

        private String email;
        private String token;

        @Override
        public void sendPasswordRecovery(String email, String token) {
            this.email = email;
            this.token = token;
        }
    }
}
