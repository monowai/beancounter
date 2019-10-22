package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
@JsonDeserialize(builder = CurrencyPair.CurrencyPairBuilder.class)
public class CurrencyPair {
  private String from;
  private String to;

  @Override
  public String toString() {
    return from + ":" + to;
  }

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class CurrencyPairBuilder {
  }

  @JsonIgnore
  public static CurrencyPair from(Currency reporting, Currency trade) {
    return CurrencyPair.builder()
        .from(reporting.getCode())
        .to(trade.getCode())
        .build();
  }
}
