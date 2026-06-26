package com.ecommerce.domain.payment.adapter.out.persistence;

import com.ecommerce.domain.payment.domain.model.Payment;
import com.ecommerce.domain.payment.domain.model.DuplicatePaymentException;
import com.ecommerce.domain.payment.domain.ports.out.PaymentRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final SpringDataPaymentRepository repository;

    public PaymentPersistenceAdapter(SpringDataPaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderIdForUpdate(UUID orderId) {
        return repository.findByOrderIdForUpdate(orderId).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity jpa = PaymentPersistenceMapper.toJpa(payment);
        PaymentJpaEntity saved = repository.save(jpa);
        return PaymentPersistenceMapper.toDomain(saved);
    }

    @Override
    public Payment saveAndFlush(Payment payment) {
        PaymentJpaEntity jpa = PaymentPersistenceMapper.toJpa(payment);
        try {
            PaymentJpaEntity saved = repository.saveAndFlush(jpa);
            return PaymentPersistenceMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicatePaymentException(e.getMessage());
        }
    }
}
