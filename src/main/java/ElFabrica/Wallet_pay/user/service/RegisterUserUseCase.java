package ElFabrica.Wallet_pay.user.service;

import ElFabrica.Wallet_pay.audit.AuditAction;
import ElFabrica.Wallet_pay.audit.AuditEntry;
import ElFabrica.Wallet_pay.audit.AuditLogger;
import ElFabrica.Wallet_pay.auth.service.EmailVerificationTokenIssuer;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private static final int CPF_LENGTH = 11;
    private static final int CNPJ_LENGTH = 14;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final CnpjValidatorGateway cnpjValidatorGateway;
    private final EmailVerificationTokenIssuer emailVerificationTokenIssuer;
    private final AuditLogger auditLogger;

    public RegisterUserUseCase(
            UserRepository userRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            CnpjValidatorGateway cnpjValidatorGateway,
            EmailVerificationTokenIssuer emailVerificationTokenIssuer,
            AuditLogger auditLogger
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.cnpjValidatorGateway = cnpjValidatorGateway;
        this.emailVerificationTokenIssuer = emailVerificationTokenIssuer;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public RegisterUserResult register(RegisterUserCommand command) {
        String email = command.email().trim().toLowerCase();
        String document = command.document().trim();

        validateDocument(document);
        ensureUniqueUserData(email, document);

        String passwordHash = passwordEncoder.encode(command.password());
        UserEntity user = new UserEntity(command.name().trim(), email, passwordHash, document);

        try {
            UserEntity savedUser = userRepository.saveAndFlush(user);
            walletRepository.saveAndFlush(new WalletEntity(savedUser));
            emailVerificationTokenIssuer.issueFor(savedUser);
            auditLogger.record(AuditEntry.success(AuditAction.USER_REGISTERED, savedUser.getId(), Map.of())
                    .withActor(savedUser.getId()));

            return new RegisterUserResult(
                    savedUser.getId(),
                    savedUser.getName(),
                    savedUser.getEmail(),
                    savedUser.isEmailVerified(),
                    savedUser.getCreatedAt()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateUserDataException("E-mail ou documento ja cadastrado");
        }
    }

    private void validateDocument(String document) {
        if (document.length() == CPF_LENGTH) {
            if (!CpfValidator.isValid(document)) {
                throw new InvalidDocumentException("CPF invalido");
            }
            return;
        }

        if (document.length() == CNPJ_LENGTH) {
            if (!cnpjValidatorGateway.exists(document)) {
                throw new InvalidDocumentException("CNPJ invalido ou nao encontrado");
            }
            return;
        }

        throw new InvalidDocumentException("Documento deve ter 11 digitos para CPF ou 14 digitos para CNPJ");
    }

    private void ensureUniqueUserData(String email, String document) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserDataException("E-mail ja cadastrado");
        }

        if (userRepository.existsByDocument(document)) {
            throw new DuplicateUserDataException("Documento ja cadastrado");
        }
    }
}
