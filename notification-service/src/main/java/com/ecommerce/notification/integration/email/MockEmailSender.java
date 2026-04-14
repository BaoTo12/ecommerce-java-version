package com.ecommerce.notification.integration.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mock email sender — logs to console instead of sending real emails.
 */
@Component
public class MockEmailSender {

    private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

    public boolean send(String recipientEmail, String subject, String body) {
        log.info("═══════════════════════════════════════════════");
        log.info("📧 SENDING EMAIL");
        log.info("   To:      {}", recipientEmail);
        log.info("   Subject: {}", subject);
        log.info("   Body:    {}", body);
        log.info("═══════════════════════════════════════════════");
        return true; // Always succeeds
    }
}
