package ElFabrica.Wallet_pay.transaction.controllers;

import ElFabrica.Wallet_pay.common.idempotency.IdempotencyKeys;
import ElFabrica.Wallet_pay.common.idempotency.IdempotentResult;
import ElFabrica.Wallet_pay.transaction.dto.ReceiptResponseDTO;
import ElFabrica.Wallet_pay.transaction.dto.TransferRequestDTO;
import ElFabrica.Wallet_pay.transaction.dto.TransferResponseDTO;
import ElFabrica.Wallet_pay.transaction.dto.TransferSenderWalletResponseDTO;
import ElFabrica.Wallet_pay.transaction.service.GetReceiptUseCase;
import ElFabrica.Wallet_pay.transaction.service.ReceiptResult;
import ElFabrica.Wallet_pay.transaction.service.TransferCommand;
import ElFabrica.Wallet_pay.transaction.service.TransferResult;
import ElFabrica.Wallet_pay.transaction.service.TransferService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransferService transferService;
    private final GetReceiptUseCase getReceiptUseCase;

    public TransactionController(TransferService transferService, GetReceiptUseCase getReceiptUseCase) {
        this.transferService = transferService;
        this.getReceiptUseCase = getReceiptUseCase;
    }

    @PostMapping("/transfers")
    ResponseEntity<TransferResponseDTO> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequestDTO request
    ) {
        IdempotentResult<TransferResult> outcome = transferService.transfer(new TransferCommand(
                UUID.fromString(jwt.getSubject()),
                request.receiverUserId(),
                request.amount(),
                request.description(),
                IdempotencyKeys.require(idempotencyKey)
        ));
        TransferResult result = outcome.result();

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

        ResponseEntity.BodyBuilder builder = ResponseEntity.created(URI.create("/transactions/" + result.transactionId()));
        if (outcome.replayed()) {
            builder.header(IdempotencyKeys.REPLAYED_HEADER, "true");
        }
        return builder.body(response);
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
