package com.ecommerce.monolith.common.exception.product;

public class ProductExceptionNotFound extends RuntimeException{
    public ProductExceptionNotFound(String message){
        super(message);
    }
}
