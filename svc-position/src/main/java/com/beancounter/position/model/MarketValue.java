package com.beancounter.position.model;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Builder;
import lombok.Data;


/**
 * Position Valuation functions.
 * 
 * @author mikeh
 * @since 2019-02-08
 */
@Data
@Builder
public class MarketValue {
  private BigDecimal price;
  private BigDecimal marketValue;
  private Date asAt;

}
