package com.beancounter.common.request;

import com.beancounter.common.model.CurrencyPair;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Collection;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = FxRequest.FxRequestBuilder.class)
public class FxRequest {
  private Date rateDate;
  private Collection<CurrencyPair> pairs;

  @JsonPOJOBuilder(withPrefix = "")
  public static class FxRequestBuilder {
  }
}
