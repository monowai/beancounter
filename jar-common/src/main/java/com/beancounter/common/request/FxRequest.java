package com.beancounter.common.request;

import com.beancounter.common.model.CurrencyPair;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Collection;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = FxRequest.FxRequestBuilder.class)
public class FxRequest {
  private String rateDate;
  private Collection<CurrencyPair> pairs;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class FxRequestBuilder {
  }
}
