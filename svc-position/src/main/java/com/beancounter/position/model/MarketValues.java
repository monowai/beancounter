package com.beancounter.position.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * A Container for MarketValue objects.
 * @author mikeh
 * @since 2019-02-14
 */
@Data
public class MarketValues {

  private Map<String, MarketValue> marketValues = new HashMap<>();

  void add(MarketValue marketValue) {
    this.marketValues.put(marketValue.getPosition().getAsset().getCode(), marketValue);
  }

}
