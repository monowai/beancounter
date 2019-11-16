package com.beancounter.marketdata.providers.wtd;

import static com.beancounter.marketdata.providers.wtd.WtdService.ID;

import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.config.StaticConfig;
import com.beancounter.marketdata.providers.DataProviderConfig;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({WtdService.class, StaticConfig.class, WtdRequester.class})
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

  @Autowired
  void setStaticConfig(StaticConfig staticConfig) {
    this.staticConfig = staticConfig;
  }

  @Override
  public String translateMarketCode(Market market) {
    return staticConfig.getMarketData().get(market.getCode()).getAliases().get(ID);
  }

  // For testing purposes - allows us to setup a static base date for which Market Prices Dates
  // can reliably computed from.
  public String getDate() {
    return (date == null ? DateUtils.today() : date);
  }

  @Override
  // Price dates are usually T-1.  Still need to implement this as a configuration by market option.
  public String getMarketDate(Market market) {
    return getMarketDate(market, getDate());
  }

  public String getMarketDate(Market market, String startDate) {
    int dayCount = 1;
    if (market.getCode().equalsIgnoreCase("NZX")) {
      dayCount = 2;
    }
    LocalDate result = DateUtils.getLastMarketDate(
        DateUtils.getDate(startDate).toInstant().atZone(ZoneId.systemDefault()),
        timeZone.toZoneId(), dayCount);

    return result.toString();
  }
}
