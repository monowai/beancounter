package com.beancounter.common.model;

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

  @JsonPOJOBuilder(withPrefix = "")
  public static class CurrencyPairBuilder {
  }

  @Override
  public String toString() {
    return from + ":" + to;
  }
}
