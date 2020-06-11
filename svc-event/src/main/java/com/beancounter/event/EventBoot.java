package com.beancounter.event;

import com.beancounter.auth.server.AuthServerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackageClasses = {AuthServerConfig.class},
    scanBasePackages = {
        "com.beancounter.event",
        "com.beancounter.client",
        "com.beancounter.common"
    })
public class EventBoot {
  public static void main(String[] args) {
    SpringApplication.run(EventBoot.class, args);
  }
}

