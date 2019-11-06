package com.beancounter.marketdata.providers.mock;

import com.beancounter.common.model.Market;
import com.beancounter.marketdata.providers.DataProviderConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@Configuration
@Import({MockProviderService.class})
public class MockConfig implements DataProviderConfig {
  @Value("${beancounter.marketdata.provider.MOCK.markets}")
  private String markets;

  @Override
  public Integer getBatchSize() {
    return null;
  }

  @Override
  public String translateMarketCode(Market market) {
    return market.getCode();
  }

  @Override
  public String getDate() {
    return null;
  }
}
