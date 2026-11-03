package ElFabrica.Wallet_pay.wallet.dto;

import jakarta.validation.constraints.Size;

public record DepositRequestDTO(
        String amount,

        @Size(max = 255, message = "Descricao deve ter no maximo 255 caracteres")
        String description
) {
}
