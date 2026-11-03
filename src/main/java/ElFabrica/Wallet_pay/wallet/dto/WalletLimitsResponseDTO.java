package ElFabrica.Wallet_pay.wallet.dto;

public record WalletLimitsResponseDTO(
        TransferLimits transfer,
        DepositLimits deposit,
        Night night
) {
    public record LimitValue(String effective, String global, String custom) {
    }

    public record PeriodLimitValue(String effective, String global, String custom, String used, String available) {
    }

    public record TransferLimits(
            LimitValue perOperation,
            PeriodLimitValue daily,
            PeriodLimitValue monthly,
            LimitValue nightPerOperation,
            PeriodLimitValue nightWindow
    ) {
    }

    public record DepositLimits(LimitValue perOperation, PeriodLimitValue daily) {
    }

    public record Night(String start, String end, String timezone, boolean active) {
    }
}
