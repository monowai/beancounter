package com.beancounter.client.ingest;

import com.beancounter.client.AssetService;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AssetIngestService {

  private final AssetService assetService;

  AssetIngestService(AssetService assetService) {
    this.assetService = assetService;
  }

  /**
   * Create assets, if necessary, and return the hydrated assets.
   *
   * @param assetCode Code on the exchange
   * @param assetName Name to set the asset to
   * @param market    exchange code
   * @return hydrated asset with a primary key.
   */
  public Asset resolveAsset(String assetCode, String assetName, Market market) {
    if (market.getCode().equalsIgnoreCase("MOCK")) {
      // Support unit testings where we don't really care about the asset
      Asset asset = AssetUtils.getAsset(assetCode, "MOCK");
      asset.setName(assetName);
      return asset;
    }
    String callerKey = AssetUtils.toKey(assetCode, market.getCode());
    AssetRequest assetRequest = AssetRequest.builder()
        .data(callerKey, Asset.builder()
            .code(assetCode)
            .name(assetName)
            .market(market)
            .build())
        .build();
    AssetUpdateResponse response = assetService.process(assetRequest);
    if (response == null) {
      throw new BusinessException(
          String.format("No response returned for %s:%s", assetCode, market.getCode()));
    }

    return response.getData().values().iterator().next();

  }


}
