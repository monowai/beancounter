package com.beancounter.marketdata.service;

import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RateCalculator {
  /**
   * For the supplied Pairs, compute the cross rates using the supplied rate table data.
   * Returns one rate for every requested CurrencyPair. The CurrencyPair serves as the callers
   * index to the result set.
   *
   * @param asAt
   * @param pairs   ISO currency codes for which to compute rates, i.e. NZD,AUD
   * @param rateMap Base->Target  (by default, USD->ISO)
   */
  public Map<CurrencyPair, FxRate> compute(Date asAt, Collection<CurrencyPair> pairs,
                                           Map<String, FxRate> rateMap) {
    Map<CurrencyPair, FxRate> results = new HashMap<>();

    for (CurrencyPair pair : pairs) { // For all requested pairings
      if (!pair.getFrom().equalsIgnoreCase(pair.getTo())) { // Is the answer one?
        FxRate from = rateMap.get(pair.getFrom());
        FxRate to = rateMap.get(pair.getTo());
        assert (from != null && to != null);

        BigDecimal rate = from.getRate().divide(to.getRate(), 8, RoundingMode.HALF_UP);

        results.put(pair, FxRate.builder()
            .from(from.getTo())
            .to(to.getTo())
            .rate(rate)
            .date(asAt)
            .build());
      } else {
        results.put(pair, FxRate.builder()
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
