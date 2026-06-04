package com.ecommerce.monolith.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(@NotBlank @Size(max = 500) String reason) {}
