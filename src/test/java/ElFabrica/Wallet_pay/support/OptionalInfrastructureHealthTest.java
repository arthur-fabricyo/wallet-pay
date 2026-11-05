package ElFabrica.Wallet_pay.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Notifications are best-effort: a broker or SMTP outage must not turn /actuator/health (the probe) DOWN. */
class OptionalInfrastructureHealthTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void brokerAndMailAreNotPartOfTheHealthEndpoint() {
        assertThat(context.containsBean("rabbitHealthContributor")).isFalse();
        assertThat(context.containsBean("mailHealthContributor")).isFalse();
    }
}
