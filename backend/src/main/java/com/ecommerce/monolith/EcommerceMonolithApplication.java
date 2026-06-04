package com.ecommerce.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcommerceMonolithApplication {
  public static void main(String[] args) {
    SpringApplication.run(EcommerceMonolithApplication.class, args);
  }
}
