package ElFabrica.Wallet_pay.audit;

public enum AuditAction {
    USER_REGISTERED(AuditResourceType.USER),
    LOGIN_SUCCEEDED(AuditResourceType.SESSION),
    LOGIN_FAILED(AuditResourceType.SESSION),
    LOGOUT(AuditResourceType.SESSION),
    EMAIL_VERIFIED(AuditResourceType.USER),
    PASSWORD_RESET_REQUESTED(AuditResourceType.USER),
    PASSWORD_RESET_COMPLETED(AuditResourceType.USER),
    TRANSFER_CREATED(AuditResourceType.TRANSACTION),
    TRANSFER_REJECTED(AuditResourceType.WALLET),
    DEPOSIT_CREATED(AuditResourceType.TRANSACTION),
    DEPOSIT_REJECTED(AuditResourceType.WALLET),
    LIMITS_UPDATED(AuditResourceType.WALLET);

    private final AuditResourceType resourceType;

    AuditAction(AuditResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public AuditResourceType resourceType() {
        return resourceType;
    }
}
