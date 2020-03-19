package com.beancounter.common.contracts;

import com.beancounter.common.model.Asset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetResponse implements Payload<Asset> {
  private Asset data;
}
