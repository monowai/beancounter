package com.beancounter.marketdata.assets.figi;

import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FigiResponse {
  private Collection<FigiAsset> data;
  private String error;
  private String next;
}
