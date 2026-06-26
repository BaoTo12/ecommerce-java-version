package com.ecommerce.domain.notification.adapter.out.persistence;

import com.ecommerce.domain.notification.domain.model.NotificationEntity;
import com.ecommerce.domain.notification.domain.ports.out.NotificationRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class NotificationPersistenceAdapter implements NotificationRepositoryPort {

    private final SpringDataNotificationRepository repository;

    public NotificationPersistenceAdapter(SpringDataNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<NotificationEntity> findByOrderIdAndType(UUID orderId, String type) {
        return repository.findByOrderIdAndType(orderId, type).map(NotificationPersistenceMapper::toDomain);
    }

    @Override
    public List<NotificationEntity> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).stream()
                .map(NotificationPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public NotificationEntity save(NotificationEntity notification) {
        NotificationJpaEntity jpa = NotificationPersistenceMapper.toJpa(notification);
        NotificationJpaEntity saved = repository.save(jpa);
        return NotificationPersistenceMapper.toDomain(saved);
    }

    @Override
    public NotificationEntity saveAndFlush(NotificationEntity notification) {
        NotificationJpaEntity jpa = NotificationPersistenceMapper.toJpa(notification);
        try {
            NotificationJpaEntity saved = repository.saveAndFlush(jpa);
            return NotificationPersistenceMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new com.ecommerce.domain.notification.domain.model.DuplicateNotificationException(e.getMessage());
        }
    }
}
