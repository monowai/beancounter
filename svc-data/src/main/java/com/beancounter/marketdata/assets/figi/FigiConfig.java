package com.beancounter.marketdata.assets.figi;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({FigiProxy.class})
@Data
public class FigiConfig {
  @Value("${beancounter.market.providers.FIGI.key:demo}")
  private String apiKey;
  @Value("${beancounter.market.providers.FIGI.enabled:true}")
  private Boolean enabled;

}
