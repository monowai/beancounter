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

  public enum In {
    LOCAL,
    PORTFOLIO,
    BASE

  }

  private Asset asset;
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

  @JsonIgnore
  public MoneyValues addMoneyValue(In valueCurrency, MoneyValues moneyValues) {
    this.moneyValues.put(valueCurrency, moneyValues);
    return moneyValues;
  }

  @Builder.Default
  @SuppressWarnings("UnusedAssignment")
  @Getter
  private QuantityValues quantityValues = QuantityValues.builder().build();

  @Builder.Default
  @SuppressWarnings("UnusedAssignment")
  private Map<In, MarketValue> marketValues = new HashMap<>();

  /**
   * Market values are tracked in various currencies.
   *
   * @param valueCurrency which currency group?
   * @return MarketValue in ValueCurrency
   */
  @JsonIgnore
  public MarketValue getMarketValue(In valueCurrency) {
    MarketValue result = marketValues.get(valueCurrency);
    if (result == null) {
      result = MarketValue.builder().build();
      marketValues.put(valueCurrency, result);
    }
    return result;
  }

  @JsonIgnore
  public MarketValue addMarkeValue(In valueCurrency, MarketValue marketValue) {
    this.marketValues.put(valueCurrency, marketValue);
    return marketValue;
  }


  private Date lastTradeDate;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class PositionBuilder {

  }

}
