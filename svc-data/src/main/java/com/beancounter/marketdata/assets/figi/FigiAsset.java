package com.beancounter.marketdata.assets.figi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FigiAsset {
  private String name;
  private String ticker;
  private String securityType2;
}
