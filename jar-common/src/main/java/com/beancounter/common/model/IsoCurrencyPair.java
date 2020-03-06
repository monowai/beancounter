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
public class IsoCurrencyPair {
  private String from;
  private String to;

  @JsonIgnore
  public static IsoCurrencyPair from(Currency reporting, Currency trade) {
    if (trade == null || reporting == null) {
      return null;

    }
    if (trade.getCode().equalsIgnoreCase(reporting.getCode())) {
      return null;
    }

    return IsoCurrencyPair.builder()
        .from(reporting.getCode())
        .to(trade.getCode())
        .build();
  }

  @Override
  public String toString() {
    return from + ":" + to;
  }
}
