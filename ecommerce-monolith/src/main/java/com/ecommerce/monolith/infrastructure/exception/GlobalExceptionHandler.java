package com.ecommerce.monolith.infrastructure.exception;

import com.ecommerce.monolith.infrastructure.resilience.RateLimitExceededException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

  // ─── Domain Exceptions ────────────────────────────────────────────────────

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "not-found");
  }

  @ExceptionHandler(BusinessRuleViolationException.class)
  public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleViolationException ex) {
    log.warn("Business rule violation: {}", ex.getMessage());
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "business-rule");
  }

  @ExceptionHandler(IllegalOrderTransitionException.class)
  public ResponseEntity<ProblemDetail> handleBadTransition(IllegalOrderTransitionException ex) {
    log.warn("Illegal order transition: {}", ex.getMessage());
    return problem(HttpStatus.CONFLICT, ex.getMessage(), "illegal-transition");
  }

  @ExceptionHandler(PriceChangedException.class)
  public ResponseEntity<ProblemDetail> handlePriceChanged(PriceChangedException ex) {
    // Edge Case #4: Price snapshot mismatch
    log.warn("Price changed at checkout: {}", ex.getMessage());
    ProblemDetail pd = buildProblemDetail(HttpStatus.CONFLICT, ex.getMessage(), "price-changed");
    pd.setProperty("currentPrice", ex.getCurrentPrice());
    pd.setProperty("snapshotPrice", ex.getSnapshotPrice());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
  }

  @ExceptionHandler(IdempotentResponseException.class)
  public ResponseEntity<?> handleIdempotent(IdempotentResponseException ex) {
    // Edge Case #1: Return cached idempotent response, same status as original
    log.info("Returning cached idempotent response for key={}", ex.getKey());
    return ResponseEntity.status(ex.getOriginalStatus()).body(ex.getCachedBody());
  }

  // ─── Infrastructure / Security ───────────────────────────────────────────

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
    // Edge Case #12: Rate limit exceeded
    log.warn("Rate limit exceeded: {}", ex.getMessage());
    ProblemDetail pd =
        buildProblemDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "rate-limit-exceeded");
    pd.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(pd);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    return problem(HttpStatus.FORBIDDEN, "Access denied", "access-denied");
  }

  @ExceptionHandler(ResourceOwnershipException.class)
  public ResponseEntity<ProblemDetail> handleOwnership(ResourceOwnershipException ex) {
    // Edge Cases #16: Address ownership check
    log.warn("Ownership violation: {}", ex.getMessage());
    return problem(HttpStatus.FORBIDDEN, ex.getMessage(), "ownership-violation");
  }

  // ─── Framework / Data ────────────────────────────────────────────────────

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    // Edge Case #20: Input validation
    Map<String, String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                    (a, b) -> a));
    ProblemDetail pd =
        buildProblemDetail(HttpStatus.BAD_REQUEST, "Request validation failed", "validation-error");
    pd.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(pd);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return problem(
        HttpStatus.CONFLICT, "Resource already exists or constraint violated", "data-integrity");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
    // Edge Case #2: exhausted retry
    log.warn("Optimistic lock exhausted: {}", ex.getMessage());
    return problem(
        HttpStatus.CONFLICT,
        "Resource was modified concurrently. Please retry.",
        "concurrent-modification");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "internal-error");
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String detail, String errorCode) {
    return ResponseEntity.status(status).body(buildProblemDetail(status, detail, errorCode));
  }

  private ProblemDetail buildProblemDetail(HttpStatus status, String detail, String errorCode) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setType(URI.create("https://ecommerce.example.com/errors/" + errorCode));
    pd.setProperty("errorCode", errorCode);
    pd.setProperty("timestamp", Instant.now().toString());
    return pd;
  }
}
