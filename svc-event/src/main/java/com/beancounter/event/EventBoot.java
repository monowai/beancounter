package com.beancounter.event;

import com.beancounter.auth.client.AuthClientConfig;
import com.beancounter.auth.server.AuthServerConfig;
import com.beancounter.common.utils.UtilConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackageClasses = {AuthServerConfig.class, UtilConfig.class, AuthClientConfig.class},
    scanBasePackages = {
        "com.beancounter.event",
        "com.beancounter.client",
        "com.beancounter.common.exception"
    })
@EntityScan("com.beancounter.common.event")
@EnableAsync
@EnableCaching
@EnableScheduling
public class EventBoot {
  public static void main(String[] args) {
    SpringApplication.run(EventBoot.class, args);
  }
}

