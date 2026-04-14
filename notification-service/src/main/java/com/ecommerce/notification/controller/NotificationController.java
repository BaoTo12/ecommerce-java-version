package com.ecommerce.notification.controller;

import com.ecommerce.notification.model.entity.NotificationEntity;
import com.ecommerce.notification.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notifRepo;

    public NotificationController(NotificationRepository notifRepo) {
        this.notifRepo = notifRepo;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<List<NotificationEntity>> getByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(notifRepo.findByOrderId(orderId));
    }
}
