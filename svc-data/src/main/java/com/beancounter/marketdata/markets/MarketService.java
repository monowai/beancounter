package com.beancounter.marketdata.markets;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.config.StaticConfig;
import java.util.HashMap;
import java.util.Map;
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
public class MarketService implements com.beancounter.client.MarketService {

  private Map<String, Market> markets;
  private final Map<String, String> aliases = new HashMap<>();

  /**
   * Return the Exchange code to use for the supplied input.
   *
   * @param input code that *might* have an alias.
   * @return the alias or input if no exception is defined.
   */
  public String resolveAlias(String input) {
    String alias = aliases.get(input.toUpperCase());
    if (alias == null) {
      return input;
    } else {
      return alias;
    }

  }

  /**
   * Resolves a market via its code property.
   *
   * @param marketCode non-null market code - can also be an alias
   * @return resolved market
   */

  public Market getMarket(String marketCode) {
    return getMarket(marketCode, true);
  }

  public Market getMarket(String marketCode, boolean orByAlias) {
    if (marketCode == null) {
      throw new BusinessException("Null Market Code");
    }
    Market market = markets.get(marketCode.toUpperCase());
    String errorMessage = String.format("Unable to resolve market code %s", marketCode);
    if (market == null && orByAlias) {
      String byAlias = resolveAlias(marketCode);
      if (byAlias == null) {
        throw new BusinessException(errorMessage);
      }
      market = markets.get(byAlias);
    }
    if (market == null) {
      throw new BusinessException(errorMessage);
    }

    return market;
  }

  public MarketResponse getMarkets() {

    return MarketResponse.builder().data(markets.values()).build();
  }

  @Autowired
  public void setMarkets(StaticConfig staticConfig) {
    this.markets = staticConfig.getMarketData();
    for (String marketCode : markets.keySet()) {
      Market market = markets.get(marketCode);
      if (!market.getAliases().isEmpty()) {
        for (String provider : market.getAliases().keySet()) {
          this.aliases
              .put(market.getAliases().get(provider).toUpperCase(), marketCode.toUpperCase());
        }
      }
    }

  }
}
