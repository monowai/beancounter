package com.beancounter.common.model;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FxRate {
  private Currency to;
  private Currency from;
  private BigDecimal rate;
  private Date date;

}
