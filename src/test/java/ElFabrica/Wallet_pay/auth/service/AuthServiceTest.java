package ElFabrica.Wallet_pay.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.auth.domain.RefreshTokenEntity;
import ElFabrica.Wallet_pay.auth.repository.RefreshTokenRepository;
import ElFabrica.Wallet_pay.common.error.ErrorCode;
import ElFabrica.Wallet_pay.support.RecordingAuditLogger;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    @Test
    void shouldAuditSuccessfulLogin() {
        UserEntity user = verifiedUser();
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        AuthService authService = new AuthService(
                new FakeUserRepository(user).proxy(),
                new FakeRefreshTokenRepository().proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                auditLogger
        );

        authService.login("joao@email.com", "Senha123");

        assertThat(auditLogger.recorded).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.LOGIN_SUCCEEDED);
            assertThat(entry.actorUserId()).isEqualTo(user.getId());
        });
        assertThat(auditLogger.failures).isEmpty();
    }

    @Test
    void shouldAuditFailedLoginWithReasonAndWithoutThePassword() {
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        AuthService authService = new AuthService(
                new FakeUserRepository(verifiedUser()).proxy(),
                new FakeRefreshTokenRepository().proxy(),
                nonMatchingPasswordEncoder(),
                fixedAccessTokenService(),
                auditLogger
        );

        assertThatThrownBy(() -> authService.login("JOAO@EMAIL.COM", "errada"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(auditLogger.failures).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.LOGIN_FAILED);
            assertThat(entry.failureCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            assertThat(entry.metadata())
                    .containsEntry("email", "joao@email.com")
                    .containsEntry("reason", "INVALID_CREDENTIALS")
                    .doesNotContainValue("errada");
        });
    }

    @Test
    void shouldAuthenticateVerifiedUserWithNormalizedEmailAndPersistRefreshToken() {
        UserEntity user = verifiedUser();
        FakeUserRepository userRepository = new FakeUserRepository(user);
        FakeRefreshTokenRepository refreshTokenRepository = new FakeRefreshTokenRepository();
        AuthService authService = new AuthService(
                userRepository.proxy(),
                refreshTokenRepository.proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        AuthTokenResult result = authService.login("JOAO@EMAIL.COM", "Senha123");

        assertThat(userRepository.emailSearched).isEqualTo("joao@email.com");
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(1200);
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(refreshTokenRepository.savedToken.getUser()).isSameAs(user);
    }

    @Test
    void shouldRejectInvalidPassword() {
        AuthService authService = new AuthService(
                new FakeUserRepository(verifiedUser()).proxy(),
                new FakeRefreshTokenRepository().proxy(),
                nonMatchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> authService.login("joao@email.com", "errada"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectMissingUser() {
        AuthService authService = new AuthService(
                new FakeUserRepository(null).proxy(),
                new FakeRefreshTokenRepository().proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> authService.login("naoexiste@email.com", "Senha123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectUnverifiedEmail() {
        AuthService authService = new AuthService(
                new FakeUserRepository(unverifiedUser()).proxy(),
                new FakeRefreshTokenRepository().proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> authService.login("joao@email.com", "Senha123"))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void shouldIssueAccessTokenFromValidRefreshToken() {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                "refresh-token",
                verifiedUser(),
                Instant.now().plusSeconds(60)
        );
        AuthService authService = new AuthService(
                new FakeUserRepository(null).proxy(),
                new FakeRefreshTokenRepository(refreshToken).proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        AccessTokenResult result = authService.refresh("refresh-token");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.expiresIn()).isEqualTo(1200);
    }

    @Test
    void shouldRejectExpiredRefreshToken() {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                "refresh-token",
                verifiedUser(),
                Instant.now().minusSeconds(1)
        );
        AuthService authService = new AuthService(
                new FakeUserRepository(null).proxy(),
                new FakeRefreshTokenRepository(refreshToken).proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldRejectMissingRefreshToken() {
        AuthService authService = new AuthService(
                new FakeUserRepository(null).proxy(),
                new FakeRefreshTokenRepository(null).proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> authService.refresh("missing-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldRejectInvalidatedRefreshToken() {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                "refresh-token",
                verifiedUser(),
                Instant.now().plusSeconds(60)
        );
        refreshToken.invalidate(Instant.now());
        AuthService authService = new AuthService(
                new FakeUserRepository(null).proxy(),
                new FakeRefreshTokenRepository(refreshToken).proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldInvalidateRefreshTokenOnLogout() {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity(
                "refresh-token",
                verifiedUser(),
                Instant.now().plusSeconds(60)
        );
        AuthService authService = new AuthService(
                new FakeUserRepository(null).proxy(),
                new FakeRefreshTokenRepository(refreshToken).proxy(),
                matchingPasswordEncoder(),
                fixedAccessTokenService(),
                new RecordingAuditLogger()
        );

        authService.logout("refresh-token");

        assertThat(refreshToken.isInvalidated()).isTrue();
        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private static AccessTokenService fixedAccessTokenService() {
        return new AccessTokenService(null) {
            @Override
            public AccessTokenResult issue(UserEntity user) {
                return new AccessTokenResult("access-token", "Bearer", 1200);
            }
        };
    }

    private static PasswordEncoder matchingPasswordEncoder() {
        return passwordEncoder(true);
    }

    private static PasswordEncoder nonMatchingPasswordEncoder() {
        return passwordEncoder(false);
    }

    private static PasswordEncoder passwordEncoder(boolean matches) {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return matches;
            }
        };
    }

    private static UserEntity verifiedUser() {
        UserEntity user = unverifiedUser();
        setField(user, "emailVerified", true);
        return user;
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
                            yield Optional.ofNullable(user);
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeRefreshTokenRepository {

        private RefreshTokenEntity savedToken;
        private RefreshTokenEntity existingToken;

        private FakeRefreshTokenRepository() {
        }

        private FakeRefreshTokenRepository(RefreshTokenEntity existingToken) {
            this.existingToken = existingToken;
        }

        private RefreshTokenRepository proxy() {
            return (RefreshTokenRepository) Proxy.newProxyInstance(
                    RefreshTokenRepository.class.getClassLoader(),
                    new Class<?>[] {RefreshTokenRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> {
                            savedToken = (RefreshTokenEntity) args[0];
                            yield savedToken;
                        }
                        case "findByToken" -> Optional.ofNullable(existingToken)
                                .filter(token -> token.getToken().equals(args[0]));
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
