package ElFabrica.Wallet_pay.transaction.service;

public class ReceiptAccessDeniedException extends RuntimeException {

    public ReceiptAccessDeniedException(String message) {
        super(message);
    }
}
