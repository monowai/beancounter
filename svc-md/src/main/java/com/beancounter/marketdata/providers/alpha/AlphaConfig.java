package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.Market;
import com.beancounter.marketdata.config.StaticConfig;
import com.beancounter.marketdata.providers.DataProviderConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({AlphaService.class, StaticConfig.class, AlphaRequester.class})
@Data
public class AlphaConfig implements DataProviderConfig {

  @Value("${beancounter.marketdata.provider.ALPHA.batchSize:2}")
  private Integer batchSize;

  @Value("${beancounter.marketdata.provider.ALPHA.markets}")
  private String markets;

  @Override
  public Integer getBatchSize() {
    return 1;
  }

  @Override
  public String translateMarketCode(Market market) {

    if (market.getCode().equalsIgnoreCase("NASDAQ")
        || market.getCode().equalsIgnoreCase("NYSE")
        || market.getCode().equalsIgnoreCase("AMEX")) {
      return null;
    }
    if (market.getCode().equalsIgnoreCase("ASX")) {
      return "AX";
    }
    return market.getCode();

  }

  @Override
  public String getDate() {
    return "2019-04-04";
  }
}
