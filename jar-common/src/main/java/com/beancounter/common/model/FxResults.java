package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = FxResults.FxResultsBuilder.class)
public class FxResults {

  Map<String, FxPairResults> data;

  @JsonPOJOBuilder(withPrefix = "")
  public static class FxResultsBuilder {
  }

}
