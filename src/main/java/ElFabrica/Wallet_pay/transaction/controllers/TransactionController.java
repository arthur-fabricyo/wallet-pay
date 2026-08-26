package ElFabrica.Wallet_pay.transaction.controllers;

import ElFabrica.Wallet_pay.transaction.dto.ReceiptResponseDTO;
import ElFabrica.Wallet_pay.transaction.dto.TransferRequestDTO;
import ElFabrica.Wallet_pay.transaction.dto.TransferResponseDTO;
import ElFabrica.Wallet_pay.transaction.dto.TransferSenderWalletResponseDTO;
import ElFabrica.Wallet_pay.transaction.service.CreateTransferUseCase;
import ElFabrica.Wallet_pay.transaction.service.GetReceiptUseCase;
import ElFabrica.Wallet_pay.transaction.service.ReceiptResult;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final CreateTransferUseCase createTransferUseCase;
    private final GetReceiptUseCase getReceiptUseCase;

    public TransactionController(CreateTransferUseCase createTransferUseCase, GetReceiptUseCase getReceiptUseCase) {
        this.createTransferUseCase = createTransferUseCase;
        this.getReceiptUseCase = getReceiptUseCase;
    }

    @PostMapping("/transfers")
    ResponseEntity<TransferResponseDTO> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferRequestDTO request
    ) {
        TransferResult result = createTransferUseCase.transfer(new TransferCommand(
                UUID.fromString(jwt.getSubject()),
                request.receiverUserId(),
                request.amount(),
                request.description()
        ));

        TransferResponseDTO response = new TransferResponseDTO(
                result.transactionId(),
                result.type(),
                result.status(),
                result.amount(),
                result.currency(),
                result.senderWalletId(),
                result.receiverWalletId(),
                new TransferSenderWalletResponseDTO(
                        result.senderWallet().id(),
                        result.senderWallet().balance(),
                        result.senderWallet().currency(),
                        result.senderWallet().updatedAt()
                ),
                result.createdAt(),
                result.completedAt()
        );

        return ResponseEntity
                .created(URI.create("/transactions/" + result.transactionId()))
                .body(response);
    }

    @GetMapping("/{transactionId}/receipt")
    ResponseEntity<ReceiptResponseDTO> receipt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transactionId
    ) {
        ReceiptResult result = getReceiptUseCase.getReceipt(UUID.fromString(jwt.getSubject()), transactionId);

        return ResponseEntity.ok(new ReceiptResponseDTO(
                result.receiptId(),
                result.transactionId(),
                result.type(),
                result.status(),
                result.amount(),
                result.currency(),
                result.senderName(),
                result.senderDocumentMasked(),
                result.receiverName(),
                result.receiverDocumentMasked(),
                result.description(),
                result.createdAt(),
                result.completedAt()
        ));
    }
}
