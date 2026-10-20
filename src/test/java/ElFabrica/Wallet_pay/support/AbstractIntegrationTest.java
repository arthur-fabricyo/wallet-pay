package ElFabrica.Wallet_pay.support;

import ElFabrica.Wallet_pay.auth.service.AccessTokenService;
import ElFabrica.Wallet_pay.user.domain.UserEntity;
import ElFabrica.Wallet_pay.user.repository.UserRepository;
import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Senha1234";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        String tables = jdbcTemplate.queryForObject("""
                select string_agg(quote_ident(tablename), ', ')
                from pg_tables
                where schemaname = 'public'
                  and tablename <> 'flyway_schema_history'
                """, String.class);
        jdbcTemplate.execute("TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE");
    }

    protected TestUser createVerifiedUser(String name, String initialBalance) {
        return transactionTemplate.execute(status -> {
            UserEntity user = new UserEntity(
                    name,
                    UUID.randomUUID() + "@email.com",
                    passwordEncoder.encode(DEFAULT_PASSWORD),
                    randomDocument()
            );
            user.markEmailVerified();
            userRepository.save(user);

            WalletEntity wallet = new WalletEntity(user);
            BigDecimal balance = new BigDecimal(initialBalance);
            if (balance.signum() > 0) {
                wallet.credit(balance);
            }
            walletRepository.save(wallet);

            return new TestUser(
                    user.getId(),
                    wallet.getId(),
                    user.getEmail(),
                    accessTokenService.issue(user).accessToken()
            );
        });
    }

    protected String bearer(TestUser user) {
        return "Bearer " + user.accessToken();
    }

    protected BigDecimal balanceOf(TestUser user) {
        return jdbcTemplate.queryForObject(
                "select balance from wallets where id = ?",
                BigDecimal.class,
                user.walletId()
        );
    }

    protected BigDecimal totalBalance() {
        return jdbcTemplate.queryForObject("select coalesce(sum(balance), 0) from wallets", BigDecimal.class);
    }

    private static String randomDocument() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(1L, 100_000_000_000L));
    }
}
