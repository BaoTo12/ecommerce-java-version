package com.ecommerce.domain.inventory.adapter.in;

import com.ecommerce.domain.inventory.domain.ports.in.InventoryUseCase;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryUseCase inventoryUseCase;

    public InventoryController(InventoryUseCase inventoryUseCase) {
        this.inventoryUseCase = inventoryUseCase;
    }

    @GetMapping("/{productId}/stock")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable UUID productId) {
        int qty = inventoryUseCase.getStock(productId);
        return ResponseEntity.ok(Map.of("productId", productId, "availableQuantity", qty));
    }
}
