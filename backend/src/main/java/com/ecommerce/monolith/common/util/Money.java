package com.ecommerce.monolith.common.util;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Currency;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Money {
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
//    @Column(nullable = false, length = 3)
//    private Currency currency;

}
