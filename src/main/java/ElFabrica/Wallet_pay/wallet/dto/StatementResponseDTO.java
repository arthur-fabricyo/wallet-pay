package ElFabrica.Wallet_pay.wallet.dto;

import java.util.List;

public record StatementResponseDTO(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<StatementEntryResponseDTO> items
) {
}
