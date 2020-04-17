package com.beancounter.common.contracts;

import com.beancounter.common.model.IsoCurrencyPair;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FxRequest {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String rateDate;
  @Builder.Default
  private Collection<IsoCurrencyPair> pairs = new ArrayList<>();

  @JsonIgnore
  private IsoCurrencyPair tradePf;
  @JsonIgnore
  private IsoCurrencyPair tradeCash;
  @JsonIgnore
  private IsoCurrencyPair tradeBase;

  @JsonIgnore
  public FxRequest add(IsoCurrencyPair isoCurrencyPair) {
    if (isoCurrencyPair == null) {
      return this;
    }

    if (!pairs.contains(isoCurrencyPair)) {
      pairs.add(isoCurrencyPair);
    }

    return this;
  }

  public void setTradePf(IsoCurrencyPair tradePf) {
    this.tradePf = tradePf;
    add(tradePf);
  }

  public void setTradeBase(IsoCurrencyPair tradeBase) {
    this.tradeBase = tradeBase;
    add(tradeBase);
  }

  public void setTradeCash(IsoCurrencyPair tradeCash) {
    this.tradeCash = tradeCash;
    add(tradeCash);
  }
}
