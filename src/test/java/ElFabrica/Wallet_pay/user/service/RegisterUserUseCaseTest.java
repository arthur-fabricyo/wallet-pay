package ElFabrica.Wallet_pay.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.auth.service.EmailVerificationTokenIssuer;
import ElFabrica.Wallet_pay.support.RecordingAuditLogger;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterUserUseCaseTest {

    @Test
    void shouldCreateUserAndWalletWithNormalizedEmailAndPasswordHash() {
        FakeUserRepository userRepository = new FakeUserRepository();
        FakeWalletRepository walletRepository = new FakeWalletRepository();
        PasswordEncoder passwordEncoder = fixedPasswordEncoder();
        CnpjValidatorGateway cnpjValidatorGateway = cnpj -> false;
        RecordingEmailVerificationTokenIssuer emailVerificationTokenIssuer = new RecordingEmailVerificationTokenIssuer();
        RegisterUserUseCase useCase = new RegisterUserUseCase(
                userRepository.proxy(),
                walletRepository.proxy(),
                passwordEncoder,
                cnpjValidatorGateway,
                emailVerificationTokenIssuer,
                new RecordingAuditLogger()
        );

        useCase.register(new RegisterUserCommand(
                "Joao Silva",
                "JOAO@EMAIL.COM",
                "Senha123",
                "52998224725"
        ));

        assertThat(userRepository.emailChecked).isEqualTo("joao@email.com");
        assertThat(userRepository.savedUser.getEmail()).isEqualTo("joao@email.com");
        assertThat(userRepository.savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(userRepository.savedUser.isEmailVerified()).isFalse();
        assertThat(walletRepository.savedWallet).isNotNull();
        assertThat(walletRepository.savedWallet.getUser()).isSameAs(userRepository.savedUser);
        assertThat(walletRepository.savedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(walletRepository.savedWallet.getCurrency()).isEqualTo("BRL");
        assertThat(emailVerificationTokenIssuer.user).isSameAs(userRepository.savedUser);
    }

    @Test
    void shouldRejectDuplicatedEmail() {
        FakeUserRepository userRepository = new FakeUserRepository();
        userRepository.emailExists = true;
        FakeWalletRepository walletRepository = new FakeWalletRepository();
        RegisterUserUseCase useCase = new RegisterUserUseCase(
                userRepository.proxy(),
                walletRepository.proxy(),
                fixedPasswordEncoder(),
                cnpj -> false,
                user -> {
                },
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
                "Joao Silva",
                "joao@email.com",
                "Senha123",
                "52998224725"
        ))).isInstanceOf(DuplicateUserDataException.class);

        assertThat(userRepository.savedUser).isNull();
        assertThat(walletRepository.savedWallet).isNull();
    }

    @Test
    void shouldValidateCnpjUsingGateway() {
        FakeUserRepository userRepository = new FakeUserRepository();
        FakeWalletRepository walletRepository = new FakeWalletRepository();
        RecordingCnpjValidatorGateway cnpjValidatorGateway = new RecordingCnpjValidatorGateway(false);
        RegisterUserUseCase useCase = new RegisterUserUseCase(
                userRepository.proxy(),
                walletRepository.proxy(),
                fixedPasswordEncoder(),
                cnpjValidatorGateway,
                user -> {
                },
                new RecordingAuditLogger()
        );

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
                "Empresa Silva",
                "empresa@email.com",
                "Senha123",
                "11222333000181"
        ))).isInstanceOf(InvalidDocumentException.class);

        assertThat(cnpjValidatorGateway.lastCnpj).isEqualTo("11222333000181");
        assertThat(userRepository.savedUser).isNull();
    }

    private static PasswordEncoder fixedPasswordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "hashed-password";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }
        };
    }

    private static final class RecordingCnpjValidatorGateway implements CnpjValidatorGateway {

        private final boolean result;
        private String lastCnpj;

        private RecordingCnpjValidatorGateway(boolean result) {
            this.result = result;
        }

        @Override
        public boolean exists(String cnpj) {
            this.lastCnpj = cnpj;
            return result;
        }
    }

    private static final class RecordingEmailVerificationTokenIssuer implements EmailVerificationTokenIssuer {

        private UserEntity user;

        @Override
        public void issueFor(UserEntity user) {
            this.user = user;
        }
    }

    private static final class FakeUserRepository {

        private boolean emailExists;
        private String emailChecked;
        private UserEntity savedUser;

        private UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[] {UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "existsByEmail" -> {
                            emailChecked = (String) args[0];
                            yield emailExists;
                        }
                        case "existsByDocument" -> false;
                        case "save", "saveAndFlush" -> {
                            savedUser = (UserEntity) args[0];
                            yield savedUser;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class FakeWalletRepository {

        private WalletEntity savedWallet;

        private WalletRepository proxy() {
            return (WalletRepository) Proxy.newProxyInstance(
                    WalletRepository.class.getClassLoader(),
                    new Class<?>[] {WalletRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save", "saveAndFlush" -> {
                            savedWallet = (WalletEntity) args[0];
                            yield savedWallet;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
