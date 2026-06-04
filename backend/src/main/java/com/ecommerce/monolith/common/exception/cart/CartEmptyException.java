package com.ecommerce.monolith.common.exception.cart;

public class CartEmptyException extends RuntimeException{
    public CartEmptyException(String message){
        super(message);
    }
}
