package ElFabrica.Wallet_pay.notification;

public enum NotificationChannelType {
    PUSH(true, false),
    EMAIL(false, true);

    private final boolean defaultEnabled;
    private final boolean userControllable;

    NotificationChannelType(boolean defaultEnabled, boolean userControllable) {
        this.defaultEnabled = defaultEnabled;
        this.userControllable = userControllable;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public boolean userControllable() {
        return userControllable;
    }
}
