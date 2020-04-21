package com.beancounter.client.ingest;

import com.beancounter.client.AssetService;
import com.beancounter.client.MarketService;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AssetIngestService {

  private final AssetService assetService;
  private final MarketService marketService;

  AssetIngestService(AssetService assetService, MarketService marketService) {
    this.assetService = assetService;
    this.marketService = marketService;
  }

  /**
   * Create assets, if necessary, and return the hydrated assets.
   *
   * @param marketCode exchange code
   * @param assetCode  Code on the exchange
   * @param assetName  Name to set the asset to
   * @return hydrated asset with a primary key.
   */
  public Asset resolveAsset(String marketCode, String assetCode, String assetName) {
    if (marketCode.equalsIgnoreCase("MOCK")) {
      // Support unit testings where we don't really care about the asset
      Asset asset = AssetUtils.getAsset("MOCK", assetCode);
      asset.setName(assetName);
      return asset;
    }
    Market market = marketService.getMarket(marketCode);
    if (market == null) {
      throw new BusinessException(String.format("Unable to resolve market [%s]", marketCode));
    }
    String callerKey = AssetUtils.toKey(assetCode, market.getCode());
    AssetRequest assetRequest = AssetRequest.builder()
        .data(callerKey, AssetInput.builder()
            .code(assetCode)
            .market(market.getCode())
            .build())
        .build();
    AssetUpdateResponse response = assetService.process(assetRequest);
    if (response == null) {
      throw new BusinessException(
          String.format("No response returned for %s:%s", assetCode, marketCode));
    }

    return response.getData().values().iterator().next();

  }


}
