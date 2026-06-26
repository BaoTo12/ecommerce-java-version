package com.ecommerce.domain.notification.domain.model;

public class DuplicateNotificationException extends RuntimeException {
    public DuplicateNotificationException(String message) {
        super(message);
    }
}
