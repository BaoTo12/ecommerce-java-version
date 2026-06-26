package com.ecommerce.domain.notification.adapter.out.email;

import com.ecommerce.domain.notification.domain.ports.out.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailSenderAdapter implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderAdapter.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[EMAIL] To: {} | Subject: {} | Body: {}", to, subject, body);
    }
}
