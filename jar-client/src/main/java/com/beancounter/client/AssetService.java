package com.beancounter.client;

import com.beancounter.auth.TokenService;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
@Slf4j
public class AssetService {

  private AssetGateway assetGateway;
  private TokenService tokenService;

  AssetService(AssetGateway assetGateway, TokenService tokenService) {
    this.assetGateway = assetGateway;
    this.tokenService = tokenService;
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
    AssetResponse assetResponse = assetGateway.assets(tokenService.getBearerToken(), assetRequest);
    if (assetResponse == null) {
      throw new BusinessException(
          String.format("No response returned for %s:%s", assetCode, market.getCode()));
    }

    return assetResponse.getData().values().iterator().next();

  }

  @FeignClient(name = "assets",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface AssetGateway {
    @PostMapping(value = "/assets",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    AssetResponse assets(@RequestHeader("Authorization") String bearerToken,
                         AssetRequest assetRequest);
  }
}
