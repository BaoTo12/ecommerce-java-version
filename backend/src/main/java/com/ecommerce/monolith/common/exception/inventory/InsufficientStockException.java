package com.ecommerce.monolith.common.exception.inventory;

public class InsufficientStockException extends RuntimeException{
    public InsufficientStockException(String message){
        super(message);
    }
}
