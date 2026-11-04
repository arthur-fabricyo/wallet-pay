package ElFabrica.Wallet_pay.notification.push;

/**
 * Contract for delivering a push notification to a user's devices. The Android app phase (Fase 7) provides the real
 * implementation (FCM); until then {@link LoggingPushGateway} only logs.
 */
public interface PushGateway {

    void send(PushMessage message);
}
