package com.beancounter.common.contracts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetSearchResult {
  private String symbol;
  private String name;
  private String type;
  private String region;
}
