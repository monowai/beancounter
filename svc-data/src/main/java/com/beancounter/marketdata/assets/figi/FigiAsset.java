package com.beancounter.marketdata.assets.figi;

import lombok.Data;

@Data
public class FigiAsset {
  private String figi;
  private String name;
  private String ticker;
  private String exchCode;
  private String securityType2;
  private String marketSector;
}
