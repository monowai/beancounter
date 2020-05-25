package com.beancounter.common.model;


import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Totals {
  @Builder.Default
  private BigDecimal total = BigDecimal.ZERO;
}
