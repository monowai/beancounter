package com.beancounter.common.helper;

import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxRate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RateCalculator {
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
  public Map<String, FxPairResults> compute(String asAt, Collection<CurrencyPair> pairs,
                                            Map<String, FxRate> rateMap) {

    Map<String, FxPairResults> results = new HashMap<>();

    for (CurrencyPair pair : pairs) { // For all requested pairings
      FxPairResults cache = results.computeIfAbsent(asAt, k -> new FxPairResults());
      if (!pair.getFrom().equalsIgnoreCase(pair.getTo())) { // Is the answer one?
        FxRate from = rateMap.get(pair.getFrom().toUpperCase());
        FxRate to = rateMap.get(pair.getTo().toUpperCase());
        assert (from != null && to != null);

        BigDecimal rate = from.getRate().divide(to.getRate(), 8, RoundingMode.HALF_UP);

        cache.getRates().put(pair, FxRate.builder()
            .from(from.getTo())
            .to(to.getTo())
            .rate(rate)
            .date(asAt)
            .build());
      } else {
        cache.getRates().put(pair, FxRate.builder()
            .from(rateMap.get(pair.getFrom()).getTo())
            .to(rateMap.get(pair.getTo()).getTo())
            .rate(BigDecimal.ONE)
            .date(asAt)
            .build());

      }
    }
    return results;
  }
}