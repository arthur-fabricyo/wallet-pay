package ElFabrica.Wallet_pay.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    @Bean
    @Primary
    CapturingEmailVerificationSender capturingEmailVerificationSender() {
        return new CapturingEmailVerificationSender();
    }

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    CapturingPasswordRecoverySender capturingPasswordRecoverySender() {
        return new CapturingPasswordRecoverySender();
    }

    // Registered as a member class of this @Import-ed configuration (component scan skips classes nested in a
    // @TestConfiguration); declaring it as an @Bean too would map /test-support/boom twice ("Ambiguous mapping").
    @RestController
    @RequestMapping("/test-support")
    public static class FailingProbeController {

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("detalhe interno secreto");
        }
    }
}
