package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.DataProviderConfig;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({AlphaService.class, AlphaProxyCache.class, AlphaPriceAdapter.class})
@Data
public class AlphaConfig implements DataProviderConfig {

  @Value("${beancounter.market.providers.ALPHA.batchSize:2}")
  private Integer batchSize;

  @Value("${beancounter.market.providers.ALPHA.markets}")
  private String markets;

  private DateUtils dateUtils = new DateUtils();

  @Override
  public Integer getBatchSize() {
    return 1;
  }

  public String translateMarketCode(Market market) {

    if (market.getCode().equalsIgnoreCase("NASDAQ")
        || market.getCode().equalsIgnoreCase("NYSE")
        || market.getCode().equalsIgnoreCase("LON")
        || market.getCode().equalsIgnoreCase("AMEX")) {
      return null;
    }
    if (market.getCode().equalsIgnoreCase("ASX")) {
      return "AX";
    }
    return market.getCode();

  }

  @Override
  public LocalDate getMarketDate(Market market, String date) {
    return dateUtils.getLastMarketDate(
        dateUtils.getDate(date == null ? dateUtils.today() : date),
        market.getTimezone().toZoneId());
  }

  @Override
  public String getPriceCode(Asset asset) {
    if (asset.getPriceSymbol() != null) {
      return asset.getPriceSymbol();
    }
    String marketCode = translateMarketCode(asset.getMarket());
    if (marketCode != null && !marketCode.isEmpty()) {
      return asset.getCode() + "." + marketCode;
    }
    return asset.getCode();
  }

  public String translateSymbol(String code) {
    return code.replace(".", "-");
  }
}
