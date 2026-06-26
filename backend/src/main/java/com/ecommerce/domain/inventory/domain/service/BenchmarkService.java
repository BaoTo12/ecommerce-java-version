package com.ecommerce.domain.inventory.domain.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.domain.inventory.domain.model.Inventory;
import com.ecommerce.domain.inventory.domain.ports.in.BenchmarkUseCase;
import com.ecommerce.domain.inventory.domain.ports.out.InventoryRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class BenchmarkService implements BenchmarkUseCase {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final InventoryRepositoryPort inventoryRepo;
    private BenchmarkService self;

    public BenchmarkService(InventoryRepositoryPort inventoryRepo) {
        this.inventoryRepo = inventoryRepo;
    }

    public void setSelf(BenchmarkService self) {
        this.self = self;
    }

    @Override
    public boolean executeBenchmarkCheckout(UUID productId, int quantity, String strategy) {
        if ("atomic".equalsIgnoreCase(strategy)) {
            return self.doAtomicDecrement(productId, quantity);
        } else {
            return self.doPessimisticReserveBenchmark(productId, quantity);
        }
    }

    public boolean doAtomicDecrement(UUID productId, int quantity) {
        int updated = inventoryRepo.atomicDecrement(productId, quantity);
        if (updated == 1) {
            log.info("Benchmark atomic reserve success: product={}, qty={}", productId, quantity);
            return true;
        }
        log.info("Benchmark atomic reserve failed (out of stock): product={}, qty={}", productId, quantity);
        return false;
    }

    public boolean doPessimisticReserveBenchmark(UUID productId, int qty) {
        Inventory inv = inventoryRepo
                .findByIdPessimisticWrite(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inventory", productId));

        if (inv.hasSufficientStock(qty)) {
            return false;
        }

        inv.reserve(qty);
        inventoryRepo.save(inv);
        log.info("Benchmark pessimistic reserve success: product={}, qty={}", productId, qty);
        return true;
    }

    @Override
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
}
