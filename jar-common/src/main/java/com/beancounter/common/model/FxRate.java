package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = FxRate.FxRateBuilder.class)
public class FxRate {
  private Currency to;
  private Currency from;
  private BigDecimal rate;
  private String date;
  @JsonIgnore
  public static final FxRate ONE = FxRate.builder().rate(BigDecimal.ONE).build();

  @JsonPOJOBuilder(withPrefix = "")
  public static class FxRateBuilder {
  }

}
