package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@EqualsAndHashCode
@RequiredArgsConstructor
@AllArgsConstructor
public class CurrencyPair {
  private String from;
  private String to;

  @JsonIgnore
  public static CurrencyPair from(Currency reporting, Currency trade) {
    return CurrencyPair.builder()
        .from(reporting.getCode())
        .to(trade.getCode())
        .build();
  }

  @Override
  public String toString() {
    return from + ":" + to;
  }
}
