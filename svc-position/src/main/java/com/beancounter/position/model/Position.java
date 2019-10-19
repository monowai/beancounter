package com.beancounter.position.model;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.QuantityValues;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * Represents an Asset held in a Portfolio.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@JsonDeserialize(builder = Position.PositionBuilder.class)
public class Position {

  private Asset asset;
  @Builder.Default
  @SuppressWarnings("UnusedAssignment")
  @Getter
  private QuantityValues quantityValues = QuantityValues.builder().build();
  private Date lastTradeDate;
  @Builder.Default
  @SuppressWarnings("UnusedAssignment")
  @Getter
  private Map<In, MoneyValues> moneyValues = new HashMap<>();

  /**
   * MoneyValues are tracked in various currencies.
   *
   * @param valueCurrency which model.
   * @return moneyValues in valueCurrency.
   */
  @JsonIgnore
  public MoneyValues getMoneyValue(In valueCurrency) {
    MoneyValues result = moneyValues.get(valueCurrency);
    if (result == null) {
      result = MoneyValues.builder().build();
      moneyValues.put(valueCurrency, result);
    }
    return result;
  }

  public enum In {
    TRADE,
    PORTFOLIO,
    BASE

  }

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class PositionBuilder {

  }

}
