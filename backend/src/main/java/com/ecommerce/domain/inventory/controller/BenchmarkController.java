package com.ecommerce.domain.inventory.controller;

import com.ecommerce.common.exception.AppException;
import com.ecommerce.domain.inventory.service.BenchmarkService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

  private final BenchmarkService benchmarkService;

  public BenchmarkController(BenchmarkService benchmarkService) {
    this.benchmarkService = benchmarkService;
  }

  @PostMapping("/checkout")
  public ResponseEntity<String> benchmarkCheckout(@RequestBody BenchmarkRequest request) {
    if (request.productId() == null) {
      throw new AppException("productId is required", HttpStatus.BAD_REQUEST);
    }
    int qty = request.quantity() != null ? request.quantity() : 1;
    String strategy = request.strategy() != null ? request.strategy() : "atomic";

    boolean success = benchmarkService.executeBenchmarkCheckout(request.productId(), qty, strategy);

    if (success) {
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"status\":\"SUCCESS\",\"message\":\"Benchmark purchase completed successfully.\"}");
    } else {
      throw new AppException("Out of stock", HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/reset")
  public ResponseEntity<String> resetStock(@RequestBody ResetRequest request) {
    if (request.productId() == null) {
      throw new AppException("productId is required", HttpStatus.BAD_REQUEST);
    }
    int quantity = request.quantity() != null ? request.quantity() : 10;
    benchmarkService.resetStock(request.productId(), quantity);
    return ResponseEntity.ok("Stock reset successfully.");
  }

  public record BenchmarkRequest(UUID productId, Integer quantity, String strategy) {}
  public record ResetRequest(UUID productId, Integer quantity) {}
}
