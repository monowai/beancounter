package com.beancounter.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.beancounter.ingest.service",
    "com.beancounter.client",
    "com.beancounter.auth",
    "com.beancounter.shell"})
public class Ingester {
  public static void main(String[] args) {
    SpringApplication.run(Ingester.class, args);
  }
}

