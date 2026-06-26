package com.ecommerce.domain.inventory.domain.ports.in;

import com.ecommerce.domain.order.domain.model.Order;
import com.ecommerce.domain.inventory.domain.model.InventoryReservation;
import java.util.UUID;

public interface InventoryUseCase {
    void reserveForOrder(Order order);
    void releaseExpiredReservation(InventoryReservation res);
    int getStock(UUID productId);
}
