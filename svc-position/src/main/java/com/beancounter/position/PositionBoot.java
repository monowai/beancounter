package com.beancounter.position;

import com.beancounter.auth.server.AuthServerConfig;
import com.beancounter.client.config.ClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(
    scanBasePackageClasses = {
        AuthServerConfig.class,
        ClientConfig.class},
    scanBasePackages = {"com.beancounter.position"})
@EnableFeignClients
public class PositionBoot {
  public static void main(String[] args) {
    SpringApplication.run(PositionBoot.class, args);
  }

}
