package com.beancounter.common.contracts;

import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import java.util.ArrayList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class PriceRequest {
  private String date;
  private Collection<AssetInput> assets;

  public static PriceRequestBuilder of(Asset asset) {
    Collection<AssetInput> assetInputs = new ArrayList<>();
    assetInputs.add(AssetInput.builder()
        .code(asset.getCode())
        .market(asset.getMarket().getCode())
        .resolvedAsset(asset)
        .build());

    return builder()
        .assets(assetInputs);
  }
}
