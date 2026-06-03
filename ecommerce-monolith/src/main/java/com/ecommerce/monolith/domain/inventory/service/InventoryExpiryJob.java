package com.ecommerce.monolith.domain.inventory.service;

import com.ecommerce.monolith.domain.inventory.entity.InventoryReservationEntity;
import com.ecommerce.monolith.domain.inventory.repository.InventoryReservationRepository;
import com.ecommerce.monolith.domain.order.entity.OrderStatusHistoryEntity;
import com.ecommerce.monolith.domain.order.enums.OrderStatus;
import com.ecommerce.monolith.domain.order.repository.OrderRepository;
import com.ecommerce.monolith.domain.order.repository.OrderStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edge Case #5 — Inventory Reservation TTL / Expiry (Reservation Leak Prevention)
 *
 * <p>Problem: User places order → inventory reserved → user never pays → stock is held indefinitely
 * → other customers can't buy → revenue loss.
 *
 * <p>Solution: Scheduled job runs every 5 minutes. Finds reservations where: - Operation = RESERVE
 * - Status = SUCCESS (stock was actually held) - releasedAt IS NULL (not already released) -
 * expiresAt < NOW (the TTL window has closed)
 *
 * <p>For each expired reservation: 1. Releases the held stock back to inventory (atomic increment)
 * 2. Marks the order as EXPIRED (via state machine) 3. Logs a history entry for audit
 *
 * <p>The TTL window (default: 30 minutes) is configurable in application.yml.
 */
@Component
public class InventoryExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(InventoryExpiryJob.class);

  private final InventoryReservationRepository reservationRepo;
  private final InventoryService inventoryService;
  private final OrderRepository orderRepo;
  private final OrderStatusHistoryRepository historyRepo;

  public InventoryExpiryJob(
      InventoryReservationRepository reservationRepo,
      InventoryService inventoryService,
      OrderRepository orderRepo,
      OrderStatusHistoryRepository historyRepo) {
    this.reservationRepo = reservationRepo;
    this.inventoryService = inventoryService;
    this.orderRepo = orderRepo;
    this.historyRepo = historyRepo;
  }

  @Scheduled(cron = "${app.inventory.reservation-expiry-check-cron:0 */5 * * * *}")
  @Transactional
  public void releaseExpiredReservations() {
    List<InventoryReservationEntity> expired =
        reservationRepo.findExpiredReservations(Instant.now());

    if (expired.isEmpty()) return;

    log.info("InventoryExpiryJob: found {} expired reservations", expired.size());

    for (InventoryReservationEntity res : expired) {
      try {
        // Release the held stock
        inventoryService.releaseExpiredReservation(res);

        // Expire the order via the state machine
        orderRepo
            .findById(res.getOrderId())
            .ifPresent(
                order -> {
                  if (order.getStatus().canTransitionTo(OrderStatus.EXPIRED)) {
                    order.transitionTo(OrderStatus.EXPIRED);
                    orderRepo.save(order);
                    historyRepo.save(
                        OrderStatusHistoryEntity.of(
                            order.getId(),
                            OrderStatus.PAYMENT_PENDING.name(),
                            OrderStatus.EXPIRED.name(),
                            "Payment window expired after " + res.getExpiresAt()));
                    log.info("Order expired due to payment timeout: orderId={}", order.getId());
                  }
                });
      } catch (Exception e) {
        log.error("Failed to release expired reservation: id={}", res.getId(), e);
      }
    }
  }
}
