package ElFabrica.Wallet_pay.notification.controllers;

import ElFabrica.Wallet_pay.notification.NotificationChannelType;
import ElFabrica.Wallet_pay.notification.dto.NotificationPreferenceResponseDTO;
import ElFabrica.Wallet_pay.notification.dto.UpdateNotificationPreferenceRequestDTO;
import ElFabrica.Wallet_pay.notification.service.NotificationPreferenceService;
import ElFabrica.Wallet_pay.notification.service.NotificationPreferenceService.ChannelPreference;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<List<NotificationPreferenceResponseDTO>> list(@AuthenticationPrincipal Jwt jwt) {
        List<NotificationPreferenceResponseDTO> body = service.list(UUID.fromString(jwt.getSubject())).stream()
                .map(NotificationPreferenceController::toDto)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{channel}")
    ResponseEntity<NotificationPreferenceResponseDTO> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable NotificationChannelType channel,
            @Valid @RequestBody UpdateNotificationPreferenceRequestDTO request
    ) {
        ChannelPreference updated = service.update(UUID.fromString(jwt.getSubject()), channel, request.enabled());
        return ResponseEntity.ok(toDto(updated));
    }

    private static NotificationPreferenceResponseDTO toDto(ChannelPreference preference) {
        return new NotificationPreferenceResponseDTO(preference.channel(), preference.enabled());
    }
}
