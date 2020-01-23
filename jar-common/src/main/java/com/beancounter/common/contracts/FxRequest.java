package com.beancounter.common.contracts;

import com.beancounter.common.model.CurrencyPair;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
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

  private Collection<CurrencyPair> pairs;

  @JsonIgnore
  public FxRequest add(CurrencyPair currencyPair) {
    if (currencyPair == null) {
      return this;
    }

    if (pairs == null) {
      pairs = new ArrayList<>();
    }

    if (!pairs.contains(currencyPair)) {
      pairs.add(currencyPair);
    }

    return this;
  }

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class FxRequestBuilder {
  }
}
