package com.ecommerce.monolith.domain.notification.controller;

import com.ecommerce.monolith.domain.notification.entity.NotificationEntity;
import com.ecommerce.monolith.domain.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationRepository notificationRepo;

  public NotificationController(NotificationRepository notificationRepo) {
    this.notificationRepo = notificationRepo;
  }

  public record NotificationResponse(
      UUID id, UUID orderId, String type, String recipient, String message, Instant createdAt) {}

  @GetMapping("/{orderId}")
  public List<NotificationResponse> getNotificationsForOrder(@PathVariable UUID orderId) {
    List<NotificationEntity> notifications = notificationRepo.findByOrderId(orderId);
    return notifications.stream()
        .map(
            n ->
                new NotificationResponse(
                    n.getId(),
                    n.getOrderId(),
                    n.getType(),
                    n.getRecipientEmail(),
                    n.getSubject(), // Use subject as message
                    n.getCreatedAt()))
        .collect(Collectors.toList());
  }
}
