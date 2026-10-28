package ElFabrica.Wallet_pay.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;

@Configuration
public class ResilienceConfig {

    @Bean
    public RetryTemplate balanceRetryTemplate() {
        RetryPolicy policy = RetryPolicy.builder()
                .includes(OptimisticLockingFailureException.class)
                .maxRetries(3)
                .delay(Duration.ofMillis(50))
                .jitter(Duration.ofMillis(25))
                .build();
        return new RetryTemplate(policy);
    }
}
