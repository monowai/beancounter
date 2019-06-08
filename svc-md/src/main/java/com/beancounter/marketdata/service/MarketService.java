package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
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

  private StaticConfig staticConfig;

  @Autowired
  void setMarkets(StaticConfig staticConfig) {
    this.staticConfig = staticConfig;
  }

  /**
   * Resolves a market via its code property.
   *
   * @param code non-null market
   * @return resolved market
   */
  public Market getMarket(@NotNull String code) {
    Objects.requireNonNull(code);
    Market market = staticConfig.getMarketData().get(code.toUpperCase());
    if (market == null) {
      throw new BusinessException(String.format("Market not found %s", code));
    }
    return market;
  }



}
