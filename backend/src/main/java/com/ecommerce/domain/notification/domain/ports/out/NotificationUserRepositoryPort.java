package com.ecommerce.domain.notification.domain.ports.out;

import java.util.Optional;
import java.util.UUID;

public interface NotificationUserRepositoryPort {
    Optional<String> findEmailByUserId(UUID userId);
}
