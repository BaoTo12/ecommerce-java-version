package com.ecommerce.domain.notification.domain.ports.out;

public interface EmailSenderPort {
    void send(String to, String subject, String body);
}
