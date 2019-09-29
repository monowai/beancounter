package com.beancounter.marketdata;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud.
 * @author mikeh
 * @since 2019-03-03
 */
@Configuration
@EnableFeignClients
@Slf4j
public class CloudConfig {
  @PostConstruct
  private void loaded() {
    log.info("Loaded");
  }
}
