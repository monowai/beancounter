package com.beancounter.common.contracts;

import com.beancounter.common.model.Positions;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = PositionResponse.PositionResponseBuilder.class)
public class PositionResponse {
  private Positions data;

  @JsonPOJOBuilder(withPrefix = "")
  public static class PositionResponseBuilder {
  }

}
