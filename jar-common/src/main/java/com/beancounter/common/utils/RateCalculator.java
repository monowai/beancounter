package com.beancounter.common.utils;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;

public final class RateCalculator {

  private RateCalculator() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * For the supplied Pairs, compute the cross rates using the supplied rate table data.
   * Returns one rate for every requested CurrencyPair. The CurrencyPair serves as the callers
   * index to the result set.
   *
   * @param asAt    Requested date
   * @param pairs   ISO currency codes for which to compute rates, i.e. NZD,AUD
   * @param rateMap Base->Target  (by default, USD->ISO)
   * @return rates for the requested pairs on the requested date.
   */
  public static FxPairResults compute(String asAt, Collection<IsoCurrencyPair> pairs,
                                      Map<String, FxRate> rateMap) {

    FxPairResults cache = new FxPairResults();
    for (IsoCurrencyPair pair : pairs) { // For all requested pairings
      if (!pair.getFrom().equalsIgnoreCase(pair.getTo())) { // Is the answer one?
        FxRate from = rateMap.get(pair.getFrom().toUpperCase());
        FxRate to = rateMap.get(pair.getTo().toUpperCase());

        BigDecimal rate = from.getRate().divide(to.getRate(), 8, RoundingMode.HALF_UP);

        cache.getRates()
            .put(pair,
                new FxRate(from.getTo(), to.getTo(), rate, from.getDate()));
      } else {
        cache.getRates()
            .put(pair,
                new FxRate(
                    rateMap.get(pair.getFrom()).getTo(),
                    rateMap.get(pair.getFrom()).getTo(),
                    BigDecimal.ONE, asAt));
      }
    }
    return cache;
  }
}
