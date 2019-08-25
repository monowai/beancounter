package com.beancounter.marketdata.providers.fxrates;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import lombok.Data;

@Data
public class EcbRates {
  private String base;
  private Date date;
  private Map<String, BigDecimal> rates;

}
