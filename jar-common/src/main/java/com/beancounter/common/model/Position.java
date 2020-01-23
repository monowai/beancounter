package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Represents an Asset held in a Portfolio.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Position {

  public enum In {
    TRADE,
    PORTFOLIO,
    BASE
  }

  @NonNull
  private Asset asset;

  @Builder.Default
  @Getter
  private QuantityValues quantityValues = QuantityValues.builder().build();

  @Builder.Default
  @Getter
  private DateValues dateValues = DateValues.builder().build();

  @Builder.Default
  @Getter
  private Map<In, MoneyValues> moneyValues = new HashMap<>();

  @JsonIgnore
  public MoneyValues getMoneyValues(In reportCurrency) {
    return moneyValues.get(reportCurrency);
  }

  /**
   * MoneyValues are tracked in various currencies.
   *
   * @param reportCurrency which model.
   * @return moneyValues in valueCurrency.
   */
  @JsonIgnore
  public MoneyValues getMoneyValues(In reportCurrency, Currency currency) {
    MoneyValues result = moneyValues.get(reportCurrency);
    if (result == null) {
      result = MoneyValues.builder()
          .currency(currency)
          .build();
      moneyValues.put(reportCurrency, result);
    }
    return result;
  }

}
