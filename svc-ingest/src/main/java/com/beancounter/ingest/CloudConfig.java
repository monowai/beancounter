package com.beancounter.ingest;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients
public class CloudConfig {
  // For reasons best known to Spring this must be in this folder
}
