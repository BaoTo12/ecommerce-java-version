package com.ecommerce.domain.user.adapter.out.persistence;

import com.ecommerce.domain.notification.domain.ports.out.NotificationUserRepositoryPort;
import com.ecommerce.domain.user.domain.model.User;
import com.ecommerce.domain.user.domain.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationUserRepositoryAdapter implements NotificationUserRepositoryPort {

    private final UserRepositoryPort userRepoPort;

    public NotificationUserRepositoryAdapter(UserRepositoryPort userRepoPort) {
        this.userRepoPort = userRepoPort;
    }

    @Override
    public Optional<String> findEmailByUserId(UUID userId) {
        return userRepoPort.findActiveById(userId).map(User::getEmail);
    }
}
