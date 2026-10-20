package ElFabrica.Wallet_pay.wallet.repository;

import ElFabrica.Wallet_pay.wallet.domain.WalletLimitEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLimitRepository extends JpaRepository<WalletLimitEntity, UUID> {
}
