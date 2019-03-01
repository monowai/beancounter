package com.beancounter.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
public class MarketDataBoot {

  public static void main(String[] args) {
    SpringApplication.run(MarketDataBoot.class, args);
  }

}

