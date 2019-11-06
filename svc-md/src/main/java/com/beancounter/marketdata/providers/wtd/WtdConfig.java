package com.beancounter.marketdata.providers.wtd;

import static com.beancounter.marketdata.providers.wtd.WtdService.ID;

import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.config.StaticConfig;
import com.beancounter.marketdata.providers.DataProviderConfig;
import java.time.Instant;
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
  private String date;

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

  @Override
  public String getDate() {
    if (date != null) {
      return date;
    }

    LocalDate result = DateUtils.getLastMarketDate(
        Instant.now().atZone(ZoneId.systemDefault()),
        timeZone.toZoneId());

    return result.toString();

  }
}
