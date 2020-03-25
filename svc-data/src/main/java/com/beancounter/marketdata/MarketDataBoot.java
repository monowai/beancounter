package com.beancounter.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.beancounter.marketdata",
    "com.beancounter.auth.server",
    "com.beancounter.client.ingest",
    "com.beancounter.client.sharesight"

})
@EntityScan("com.beancounter.common.model")
public class MarketDataBoot {
  public static void main(String[] args) {
    SpringApplication.run(MarketDataBoot.class, args);
  }
}

