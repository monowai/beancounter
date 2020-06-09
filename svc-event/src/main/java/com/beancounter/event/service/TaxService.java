package com.beancounter.event.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TaxService {
  private final Map<String, BigDecimal> rates = new HashMap<>();

  public TaxService() {
    rates.put("USD", new BigDecimal(".30"));
  }

  public BigDecimal getRate(String code) {
    BigDecimal rate = rates.get(code);
    if (rate == null) {
      return BigDecimal.ZERO;
    }
    return rate;
  }
}
