package ElFabrica.Wallet_pay.notification.service;

import ElFabrica.Wallet_pay.notification.NotificationChannelType;
import ElFabrica.Wallet_pay.notification.repository.NotificationPreferenceRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    public record ChannelPreference(NotificationChannelType channel, boolean enabled) {
    }

    private final NotificationPreferenceRepository repository;
    private final Clock clock;

    public NotificationPreferenceService(NotificationPreferenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId, NotificationChannelType channel) {
        return repository.findByUserId(userId).getOrDefault(channel, channel.defaultEnabled());
    }

    @Transactional(readOnly = true)
    public List<ChannelPreference> list(UUID userId) {
        Map<NotificationChannelType, Boolean> stored = repository.findByUserId(userId);
        return Arrays.stream(NotificationChannelType.values())
                .map(channel -> new ChannelPreference(channel, stored.getOrDefault(channel, channel.defaultEnabled())))
                .toList();
    }

    @Transactional
    public ChannelPreference update(UUID userId, NotificationChannelType channel, boolean enabled) {
        if (!channel.userControllable()) {
            throw new InvalidNotificationPreferenceException("Canal " + channel.name() + " nao pode ser alterado");
        }
        repository.upsert(userId, channel, enabled, clock.instant());
        return new ChannelPreference(channel, enabled);
    }
}
