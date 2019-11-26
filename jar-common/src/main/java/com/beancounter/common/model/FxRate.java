package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class FxRate {
  @JsonIgnore
  public static final FxRate ONE = FxRate.builder().rate(BigDecimal.ONE).build();
  private Currency to;
  private Currency from;
  private BigDecimal rate;
  private String date;

}
