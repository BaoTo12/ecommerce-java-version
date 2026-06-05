package com.ecommerce.monolith.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
@Builder
@AllArgsConstructor
public class AppException extends RuntimeException {
  private HttpStatusCode statusCode;
  private String message;
  private String errorCode;

  public AppException(String message, HttpStatusCode statusCode) {
    super(message);
    this.message = message;
    this.statusCode = statusCode;
  }
}
