package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Verification of Market related functions.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Service
@Slf4j
public class MarketService {

  private MarketConfig marketConfig;

  @Autowired
  void setMarkets(MarketConfig marketConfig) {
    this.marketConfig = marketConfig;
  }

  /**
   * Resolves a market via its code property.
   *
   * @param code non-null market
   * @return resolved market
   */
  public Market getMarket(@NotNull String code) {
    Objects.requireNonNull(code);
    Market market = marketConfig.getMarketMap().get(code.toUpperCase());
    if (market == null) {
      throw new BusinessException(String.format("Market not found %s", code));
    }
    return market;
  }

  /**
   * Identify a date to query a market on taking into account timezones and working days.
   * Subtracts a day until it finds a working one, which may prove less than ideal.

   * For instance - Sunday 7th in Singapore will result to Friday 5th in New York
   *
   * @param requestedDate date the user is requesting in _their_ timezone
   * @param market        market to locate requestedDate on
   * @return resolved Date
   */
  public LocalDate getLastMarketDate(ZonedDateTime requestedDate, Market market) {
    Objects.requireNonNull(requestedDate);
    Objects.requireNonNull(market);

    ZonedDateTime result = requestedDate.toLocalDateTime().atZone(market.getTimezone().toZoneId());

    while (!isWorkDay(result)) {
      result = result.minusDays(1);
    }

    return result.toLocalDate();
  }


  private boolean isWorkDay(ZonedDateTime evaluate) {
    // Naive implementation that is only aware of Western markets
    if (evaluate.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
      return false;
    } else if (evaluate.getDayOfWeek().equals(DayOfWeek.SATURDAY)) {
      return false;
    }

    // market holidays...
    return marketConfig.isWorkDay(evaluate);
  }

}
