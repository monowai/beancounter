package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = DateValues.DateValuesBuilder.class)
public class DateValues {

  private String opened;
  private String last;
  private String closed;

  @JsonPOJOBuilder(withPrefix = "")
  public static class DateValuesBuilder {

  }
}