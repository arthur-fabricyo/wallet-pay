package ElFabrica.Wallet_pay.auth.controllers;

import ElFabrica.Wallet_pay.common.error.ErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ElFabrica.Wallet_pay.auth.service.AccessTokenResult;
import ElFabrica.Wallet_pay.auth.service.AuthService;
import ElFabrica.Wallet_pay.auth.service.AuthTokenResult;
import ElFabrica.Wallet_pay.auth.service.EmailNotVerifiedException;
import ElFabrica.Wallet_pay.auth.service.EmailVerificationService;
import ElFabrica.Wallet_pay.auth.service.EmailVerificationTokenException;
import ElFabrica.Wallet_pay.auth.service.InvalidCredentialsException;
import ElFabrica.Wallet_pay.auth.service.InvalidRefreshTokenException;
import ElFabrica.Wallet_pay.auth.service.PasswordRecoveryService;
import ElFabrica.Wallet_pay.auth.service.PasswordResetTokenException;
import ElFabrica.Wallet_pay.config.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.TestBeans.class})
@TestPropertySource(properties = "wallet-pay.auth.jwt-secret=wallet-pay-test-secret-with-32-bytes")
class AuthControllerTest {

    private static final String SECRET = "wallet-pay-test-secret-with-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeAuthService authService;

    @Autowired
    private FakeEmailVerificationService emailVerificationService;

    @Autowired
    private FakePasswordRecoveryService passwordRecoveryService;

    @BeforeEach
    void setUp() {
        authService.reset();
        emailVerificationService.reset();
        passwordRecoveryService.reset();
    }

    @Test
    void shouldLoginAndReturnTokens() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "joao@email.com",
                                  "password": "Senha123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1200))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        assertThat(authService.loginEmail).isEqualTo("joao@email.com");
        assertThat(authService.loginPassword).isEqualTo("Senha123");
    }

    @Test
    void shouldReturnUnauthorizedForInvalidLogin() throws Exception {
        authService.loginException = new InvalidCredentialsException("Credenciais invalidas");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "joao@email.com",
                                  "password": "errada"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldReturnForbiddenWhenEmailIsNotVerified() throws Exception {
        authService.loginException = new EmailNotVerifiedException("E-mail ainda nao verificado");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "joao@email.com",
                                  "password": "Senha123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void shouldRejectInvalidLoginPayload() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "email-invalido",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void shouldRefreshAccessToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1200));

        assertThat(authService.refreshToken).isEqualTo("refresh-token");
    }

    @Test
    void shouldReturnUnauthorizedForInvalidRefreshToken() throws Exception {
        authService.refreshException = new InvalidRefreshTokenException("Refresh token invalido");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogout() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), Instant.now().plusSeconds(60)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(authService.logoutToken).isEqualTo("refresh-token");
    }

    @Test
    void shouldRejectLogoutWithoutJwt() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldResendEmailVerificationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "joao@email.com"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(emailVerificationService.resendEmail).isEqualTo("joao@email.com");
    }

    @Test
    void shouldReturnBadRequestForInvalidEmailVerificationToken() throws Exception {
        emailVerificationService.confirmException = new EmailVerificationTokenException(
                ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                "Token de verificacao invalido"
        );

        mockMvc.perform(post("/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void shouldRequestPasswordRecoveryWithoutRevealingUserExistence() throws Exception {
        mockMvc.perform(post("/auth/password-recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@email.com"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(passwordRecoveryService.requestEmail).isEqualTo("missing@email.com");
    }

    @Test
    void shouldResetPassword() throws Exception {
        mockMvc.perform(post("/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "NovaSenha123"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(passwordRecoveryService.resetToken).isEqualTo("reset-token");
        assertThat(passwordRecoveryService.newPassword).isEqualTo("NovaSenha123");
    }

    @Test
    void shouldReturnBadRequestForInvalidPasswordResetToken() throws Exception {
        passwordRecoveryService.resetException = new PasswordResetTokenException(
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID,
                "Token de redefinicao de senha invalido"
        );

        mockMvc.perform(post("/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "invalid-token",
                                  "newPassword": "NovaSenha123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void shouldRejectInvalidPasswordResetPayload() throws Exception {
        mockMvc.perform(post("/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "somenteletras"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    private static String token(UUID userId, Instant expiresAt) throws Exception {
        Instant issuedAt = expiresAt.minusSeconds(60);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        FakeAuthService fakeAuthService() {
            return new FakeAuthService();
        }

        @Bean
        FakeEmailVerificationService fakeEmailVerificationService() {
            return new FakeEmailVerificationService();
        }

        @Bean
        FakePasswordRecoveryService fakePasswordRecoveryService() {
            return new FakePasswordRecoveryService();
        }
    }

    static class FakeAuthService extends AuthService {

        private String loginEmail;
        private String loginPassword;
        private RuntimeException loginException;
        private String refreshToken;
        private RuntimeException refreshException;
        private String logoutToken;

        FakeAuthService() {
            super(null, null, null, null);
        }

        @Override
        public AuthTokenResult login(String email, String password) {
            this.loginEmail = email;
            this.loginPassword = password;
            if (loginException != null) {
                throw loginException;
            }
            return new AuthTokenResult("access-token", "Bearer", 1200, "refresh-token");
        }

        @Override
        public AccessTokenResult refresh(String token) {
            this.refreshToken = token;
            if (refreshException != null) {
                throw refreshException;
            }
            return new AccessTokenResult("new-access-token", "Bearer", 1200);
        }

        @Override
        public void logout(String token) {
            this.logoutToken = token;
        }

        private void reset() {
            loginEmail = null;
            loginPassword = null;
            loginException = null;
            refreshToken = null;
            refreshException = null;
            logoutToken = null;
        }
    }

    static class FakeEmailVerificationService extends EmailVerificationService {

        private String resendEmail;
        private String confirmToken;
        private RuntimeException confirmException;

        FakeEmailVerificationService() {
            super(null, null, null, 24);
        }

        @Override
        public void resend(String email) {
            this.resendEmail = email;
        }

        @Override
        public void confirm(String token) {
            this.confirmToken = token;
            if (confirmException != null) {
                throw confirmException;
            }
        }

        private void reset() {
            resendEmail = null;
            confirmToken = null;
            confirmException = null;
        }
    }

    static class FakePasswordRecoveryService extends PasswordRecoveryService {

        private String requestEmail;
        private String resetToken;
        private String newPassword;
        private RuntimeException resetException;

        FakePasswordRecoveryService() {
            super(null, null, null, null, null, 30);
        }

        @Override
        public void request(String email) {
            this.requestEmail = email;
        }

        @Override
        public void reset(String tokenValue, String newPassword) {
            this.resetToken = tokenValue;
            this.newPassword = newPassword;
            if (resetException != null) {
                throw resetException;
            }
        }

        private void reset() {
            requestEmail = null;
            resetToken = null;
            newPassword = null;
            resetException = null;
        }
    }
}
