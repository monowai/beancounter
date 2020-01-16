package com.beancounter.common.contracts;

import com.beancounter.common.model.Asset;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetResponse {
  @Singular
  private Map<String, Asset> assets;
}
