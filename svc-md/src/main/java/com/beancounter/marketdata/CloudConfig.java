package com.beancounter.marketdata;

import javax.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud.
 * @author mikeh
 * @since 2019-03-03
 */
@Configuration
@EnableFeignClients
@Log4j2
public class CloudConfig {
  @PostConstruct
  void loaded() {
    log.info("Loaded");
  }
}
