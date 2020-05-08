package com.beancounter.marketdata.providers.wtd;

import static com.beancounter.marketdata.providers.wtd.WtdService.ID;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.config.StaticConfig;
import com.beancounter.marketdata.providers.DataProviderConfig;
import java.time.LocalDate;
import java.util.TimeZone;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({WtdService.class, StaticConfig.class, WtdProxy.class, WtdAdapter.class})
@Data
public class WtdConfig implements DataProviderConfig {

  @Value("${beancounter.marketdata.provider.WTD.batchSize:2}")
  private Integer batchSize;

  @Value("${beancounter.marketdata.provider.WTD.date:#{null}}")
  private String date; // Static date

  @Value("${beancounter.marketdata.provider.WTD.markets}")
  private String markets;

  private TimeZone timeZone = TimeZone.getTimeZone("US/Eastern");
  private StaticConfig staticConfig;

  private DateUtils dateUtils = new DateUtils();

  @Autowired
  void setStaticConfig(StaticConfig staticConfig) {
    this.staticConfig = staticConfig;
  }

  private String translateMarketCode(Market market) {
    return staticConfig.getMarketData().get(market.getCode()).getAliases().get(ID);
  }

  // For testing purposes - allows us to setup a static base date for which Market Prices Dates
  // can reliably computed from.
  public String getDate() {
    return (date == null ? dateUtils.today() : date);
  }

  public LocalDate getMarketDate(Market market, String startDate) {
    int daysToSubtract = 0;
    if (dateUtils.isToday(startDate)) {
      // If Current, price date is T-daysToSubtract
      daysToSubtract = 1;
      if (market.getCode().equalsIgnoreCase("NZX")) {
        daysToSubtract = 2;
      }
    }

    // If startDate is not "TODAY", assume nothing, just discount the weekends
    return dateUtils.getLastMarketDate(
        dateUtils.getDate(startDate == null ? dateUtils.today() : startDate),
        timeZone.toZoneId(), daysToSubtract);

  }

  @Override
  public String getPriceCode(Asset asset) {
    String marketCode = translateMarketCode(asset.getMarket());
    if (marketCode != null && !marketCode.isEmpty()) {
      return asset.getCode() + "." + marketCode;
    }
    return asset.getCode();
  }
}
