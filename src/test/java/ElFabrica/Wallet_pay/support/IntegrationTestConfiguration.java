package ElFabrica.Wallet_pay.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

    // One set of containers for the whole test JVM: every distinct Spring context (tests with their own nested
    // configuration get their own cached context) reuses them instead of starting a new Postgres/RabbitMQ/Mailpit.
    // Contexts stay isolated on the broker through a per-context walletpay.events.namespace.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-management-alpine");
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return RABBIT;
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        return MAILPIT;
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
        };
    }

    @Bean
    MailpitClient mailpitClient(GenericContainer<?> mailpitContainer) {
        return new MailpitClient("http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
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
