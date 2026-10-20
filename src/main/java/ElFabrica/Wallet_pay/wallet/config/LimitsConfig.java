package ElFabrica.Wallet_pay.wallet.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LimitsProperties.class)
public class LimitsConfig {
}
