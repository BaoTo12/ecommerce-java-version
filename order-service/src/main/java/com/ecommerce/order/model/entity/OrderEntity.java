package com.ecommerce.order.model.entity;

import com.ecommerce.order.model.enums.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Version
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}

    public static OrderEntity create(UUID userId, List<OrderItemEntity> items) {
        OrderEntity order = new OrderEntity();
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemEntity item : items) {
            item.setOrder(order);
            order.items.add(item);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.totalAmount = total;
        return order;
    }

    public void transitionTo(OrderStatus next) {
        this.status.validateTransition(next);
        this.status = next;
        this.updatedAt = Instant.now();
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItemEntity> getItems() { return items; }
}
