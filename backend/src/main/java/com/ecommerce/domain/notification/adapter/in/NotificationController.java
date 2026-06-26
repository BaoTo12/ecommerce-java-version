package com.ecommerce.domain.notification.adapter.in;

import com.ecommerce.domain.notification.domain.ports.in.NotificationUseCase;
import com.ecommerce.domain.notification.domain.model.NotificationEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationUseCase notificationUseCase;

    public NotificationController(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    public record NotificationResponse(
            UUID id, UUID orderId, String type, String recipient, String message, Instant createdAt) {}

    @GetMapping("/{orderId}")
    public List<NotificationResponse> getNotificationsForOrder(@PathVariable UUID orderId) {
        List<NotificationEntity> notifications = notificationUseCase.getNotificationsForOrder(orderId);
        return notifications.stream()
                .map(
                        n ->
                                new NotificationResponse(
                                        n.getId(),
                                        n.getOrderId(),
                                        n.getType(),
                                        n.getRecipientEmail(),
                                        n.getSubject(),
                                        n.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
