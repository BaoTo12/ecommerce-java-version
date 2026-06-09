package com.ecommerce.domain.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockEmailSender {

  private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

  public void send(String to, String subject, String body) {
    // Simulate sending an email
    log.info("[EMAIL] To: {} | Subject: {} | Body: {}", to, subject, body);
    // In production: mailSender.send(buildMessage(to, subject, body));
  }
}
