package com.beancounter.common.input;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class PortfolioInput {
  private String code;
  private String name;
  private String currency;
  @Builder.Default
  private String base = "USD";
}
