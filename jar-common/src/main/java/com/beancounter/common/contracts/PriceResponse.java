package com.beancounter.common.contracts;

import com.beancounter.common.model.MarketData;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Collection;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = PriceResponse.PriceResponseBuilder.class)
public class PriceResponse {

  private Collection<MarketData> data;

  @JsonPOJOBuilder(withPrefix = "")
  public static class PriceResponseBuilder {
  }

}
