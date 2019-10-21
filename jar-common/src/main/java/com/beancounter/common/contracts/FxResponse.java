package com.beancounter.common.contracts;

import com.beancounter.common.model.FxPairResults;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = FxResponse.FxResponseBuilder.class)
public class FxResponse {

  private FxPairResults data;

  @JsonPOJOBuilder(withPrefix = "")
  public static class FxResponseBuilder {
  }

}
