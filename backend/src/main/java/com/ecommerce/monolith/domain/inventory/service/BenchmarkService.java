package com.ecommerce.monolith.domain.inventory.service;

import com.ecommerce.monolith.common.exception.ResourceNotFoundException;
import com.ecommerce.monolith.domain.inventory.entity.Inventory;
import com.ecommerce.monolith.domain.inventory.repository.InventoryRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenchmarkService {

  private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

  private final InventoryRepository inventoryRepo;
  private final BenchmarkService self;
  private final int maxRetry;
  private final long backoffMs;

  public BenchmarkService(
      InventoryRepository inventoryRepo,
      @Lazy BenchmarkService self,
      @Value("${app.retry.max-attempts:3}") int maxRetry,
      @Value("${app.retry.backoff-ms:50}") long backoffMs) {
    this.inventoryRepo = inventoryRepo;
    this.self = self;
    this.maxRetry = maxRetry;
    this.backoffMs = backoffMs;
  }

  public boolean executeBenchmarkCheckout(UUID productId, int quantity, String strategy) {
    if ("atomic".equalsIgnoreCase(strategy)) {
      return self.doAtomicDecrement(productId, quantity);
    } else if ("pessimistic".equalsIgnoreCase(strategy)) {
      return self.doPessimisticReserveBenchmark(productId, quantity);
    } else {
      // Fallback for legacy k6 scripts using 'optimistic' parameters
      return self.doPessimisticReserveBenchmark(productId, quantity);
    }
  }

  @Transactional
  public boolean doAtomicDecrement(UUID productId, int quantity) {
    int updated = inventoryRepo.atomicDecrement(productId, quantity);
    if (updated == 1) {
      log.info("Benchmark atomic reserve success: product={}, qty={}", productId, quantity);
      return true;
    }
    log.info("Benchmark atomic reserve failed (out of stock): product={}, qty={}", productId, quantity);
    return false;
  }

  @Transactional
  public boolean doPessimisticReserveBenchmark(UUID productId, int qty) {
    Inventory inv =
        inventoryRepo
            .findByIdPessimisticWrite(productId)
            .orElseThrow(() -> ResourceNotFoundException.of("Inventory", productId));

    if (!inv.hasSufficientStock(qty)) {
      return false;
    }

    inv.reserve(qty);
    inventoryRepo.saveAndFlush(inv); // Force Hibernate to execute SQL update immediately, releasing pessimistic lock on commit
    log.info("Benchmark pessimistic reserve success: product={}, qty={}", productId, qty);
    return true;
  }

  @Transactional
  public void resetStock(UUID productId, int quantity) {
    Inventory inv = inventoryRepo.findById(productId).orElse(null);
    if (inv == null) {
      inv = Inventory.builder()
          .productId(productId)
          .quantity(quantity)
          .build();
    } else {
      inv = Inventory.builder()
          .productId(productId)
          .quantity(quantity)
          .version(inv.getVersion())
          .build();
    }
    inventoryRepo.save(inv);
    log.info("[DATABASE CHANGE] Reset stock of product {} to {}", productId, quantity);
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
