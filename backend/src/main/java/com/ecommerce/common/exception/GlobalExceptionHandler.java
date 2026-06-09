package com.ecommerce.common.exception;

import com.ecommerce.common.resilience.RateLimitExceededException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Centralized exception handler — maps domain exceptions to proper HTTP responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiResponse<?>> handleAppException(AppException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(
            ApiResponse.builder()
                .code(exception.getStatusCode().value())
                .message(exception.getMessage())
                .build());
  }

  // ─── Domain Exceptions ────────────────────────────────────────────────────

  @ExceptionHandler(PriceChangedException.class)
  public ResponseEntity<ApiResponse<?>> handlePriceChanged(PriceChangedException ex) {
    // Edge Case #4: Price snapshot mismatch
    log.warn("Price changed at checkout: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.builder()
                .code(HttpStatus.CONFLICT.value())
                .message(ex.getMessage())
                .result(Map.of(
                    "currentPrice", ex.getCurrentPrice(),
                    "snapshotPrice", ex.getSnapshotPrice()
                ))
                .build());
  }

  @ExceptionHandler(IdempotentResponseException.class)
  public ResponseEntity<?> handleIdempotent(IdempotentResponseException ex) {
    // Edge Case #1: Return cached idempotent response, same status as original
    log.info("Returning cached idempotent response for key={}", ex.getKey());
    return ResponseEntity.status(ex.getOriginalStatus()).body(ex.getCachedBody());
  }

  // ─── Infrastructure / Security ───────────────────────────────────────────

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ApiResponse<?>> handleRateLimit(RateLimitExceededException ex) {
    // Edge Case #12: Rate limit exceeded
    log.warn("Rate limit exceeded: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(
            ApiResponse.builder()
                .code(HttpStatus.TOO_MANY_REQUESTS.value())
                .message(ex.getMessage())
                .result(Map.of("retryAfterSeconds", ex.getRetryAfterSeconds()))
                .build());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            ApiResponse.builder()
                .code(HttpStatus.FORBIDDEN.value())
                .message("Access denied")
                .build());
  }

  // ─── Framework / Data ────────────────────────────────────────────────────

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
    // Edge Case #20: Input validation
    Map<String, String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                    (a, b) -> a));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Request validation failed")
                .result(errors)
                .build());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.builder()
                .code(HttpStatus.CONFLICT.value())
                .message("Resource already exists or constraint violated")
                .build());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<?>> handleOptimisticLock(OptimisticLockingFailureException ex) {
    // Edge Case #2: exhausted retry
    log.warn("Optimistic lock exhausted: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiResponse.builder()
                .code(HttpStatus.CONFLICT.value())
                .message("Resource was modified concurrently. Please retry.")
                .build());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiResponse.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .build());
  }
}
