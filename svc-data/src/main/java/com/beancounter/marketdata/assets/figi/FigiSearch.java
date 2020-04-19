package com.beancounter.marketdata.assets.figi;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FigiSearch {
  @Builder.Default
  private String idType = "BASE_TICKER";
  private String idValue;
  private String exchCode;
  private String securityType2;
}
