package com.beancounter.marketdata.assets.figi;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FigiSearch {
  private String query;
  private String exchCode;
  @Builder.Default
  private String securityType = "Common Stock";
  private String start;
}
