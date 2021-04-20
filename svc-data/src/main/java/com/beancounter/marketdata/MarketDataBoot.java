package com.beancounter.marketdata;

import com.beancounter.auth.server.AuthServerConfig;
import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.client.sharesight.ShareSightConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Boot all the things.
 */
@SpringBootApplication(
    scanBasePackageClasses = {
        AuthServerConfig.class,
        FxTransactions.class,
        ShareSightConfig.class},
    scanBasePackages = {
        "com.beancounter.marketdata",
        "com.beancounter.common.utils",
        "com.beancounter.common.exception"
    })
@EntityScan("com.beancounter.common.model")
public class MarketDataBoot {
  public static void main(String[] args) {
    SpringApplication.run(MarketDataBoot.class, args);
  }
}

