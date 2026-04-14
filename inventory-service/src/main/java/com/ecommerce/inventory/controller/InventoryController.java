package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.model.dto.InventoryResponse;
import com.ecommerce.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getStock(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryService.getStock(productId));
    }
}
