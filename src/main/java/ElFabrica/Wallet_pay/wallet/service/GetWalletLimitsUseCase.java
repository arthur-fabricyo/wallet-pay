package ElFabrica.Wallet_pay.wallet.service;

import ElFabrica.Wallet_pay.wallet.domain.WalletEntity;
import ElFabrica.Wallet_pay.wallet.repository.WalletRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetWalletLimitsUseCase {

    private final WalletRepository walletRepository;
    private final OperationLimitService operationLimitService;
    private final Clock clock;

    public GetWalletLimitsUseCase(
            WalletRepository walletRepository,
            OperationLimitService operationLimitService,
            Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.operationLimitService = operationLimitService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WalletLimitsResult getLimits(UUID userId) {
        WalletEntity wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Carteira nao encontrada para o usuario autenticado"));
        return operationLimitService.describe(wallet.getId(), clock.instant());
    }
}
