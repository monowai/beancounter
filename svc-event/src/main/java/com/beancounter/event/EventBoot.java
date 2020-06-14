package com.beancounter.event;

import com.beancounter.auth.server.AuthServerConfig;
import com.beancounter.common.utils.UtilConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
    scanBasePackageClasses = {AuthServerConfig.class, UtilConfig.class},
    scanBasePackages = {
        "com.beancounter.event",
        "com.beancounter.client",
        "com.beancounter.common.exception",
        "com.beancounter.auth"
    })
@EntityScan("com.beancounter.common.event")
@EnableAsync
public class EventBoot {
  public static void main(String[] args) {
    SpringApplication.run(EventBoot.class, args);
  }
}

