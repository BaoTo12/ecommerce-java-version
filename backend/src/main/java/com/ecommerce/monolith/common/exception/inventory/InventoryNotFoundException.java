package com.ecommerce.monolith.common.exception.inventory;


public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(String message){
        super(message);
    }
}
