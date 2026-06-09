package com.ecommerce.domain.inventory.controller;

import com.ecommerce.domain.inventory.service.InventoryService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

  private final InventoryService inventoryService;

  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @GetMapping("/{productId}/stock")
  public ResponseEntity<Map<String, Object>> getStock(@PathVariable UUID productId) {
    int qty = inventoryService.getStock(productId);
    return ResponseEntity.ok(Map.of("productId", productId, "availableQuantity", qty));
  }
}
