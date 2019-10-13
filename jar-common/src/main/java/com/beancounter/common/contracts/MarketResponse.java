package com.beancounter.common.contracts;

import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Collection;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = MarketResponse.MarketResponseBuilder.class)
public class MarketResponse {
  private Collection<Market> data;

  @JsonPOJOBuilder(withPrefix = "")
  public static class MarketResponseBuilder {
  }
}
