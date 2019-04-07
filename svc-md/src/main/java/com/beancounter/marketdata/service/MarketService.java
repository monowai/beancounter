package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.TimeZone;

/**
 * Access to Market data
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Service
public class MarketService {

  private MarketConfig marketConfig;

  @Autowired
  void setMarkets(MarketConfig marketConfig) {
    this.marketConfig = marketConfig;
  }

  public Market getMarket(String code) {
    Market market = marketConfig.getMarketMap().get(code);
    if (market == null) {
      throw new BusinessException(String.format("Market not found %s", code));
    }
    return market;
  }

  public LocalDateTime getLastMarketDate(ZonedDateTime requestedDate, TimeZone mdProviderTz) {
    LocalDateTime result = requestedDate.toLocalDateTime();
    while (!isWorkDay(result.atZone(mdProviderTz.toZoneId()))){
      result = result.minusDays(1);
    }
    return result;
  }


  private boolean isWorkDay(ZonedDateTime mdDate) {
    if ( mdDate.getDayOfWeek().equals(DayOfWeek.SUNDAY))
      return false;
    if ( mdDate.getDayOfWeek().equals(DayOfWeek.SATURDAY))
      return false;
    // market holidays...
    return marketConfig.isWorkDay();
  }

}
