package com.ecommerce.domain.inventory.domain.ports.in;

import java.util.UUID;

public interface BenchmarkUseCase {
    boolean executeBenchmarkCheckout(UUID productId, int quantity, String strategy);
    void resetStock(UUID productId, int quantity);
}
