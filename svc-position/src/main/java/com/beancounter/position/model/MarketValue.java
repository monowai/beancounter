package com.beancounter.position.model;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Price;
import java.math.BigDecimal;
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
  private Price price;
  private Position position;

  // Local Market Value in market currency
  public BigDecimal getMarketValue() {
    return price.getPrice().multiply(position.getQuantityValues().getTotal());
  }

  public BigDecimal getMarketCost() {
    return position.getMoneyValues().getMarketCost();
  }
}
