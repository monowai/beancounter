package com.beancounter.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("com.beancounter.common.model")
public class MarketDataBoot {
  public static void main(String[] args) {
    SpringApplication.run(MarketDataBoot.class, args);
  }
}

