package com.beancounter.client.services;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.AssetService;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

public class AssetServiceClient implements AssetService {

  private final AssetGateway assetGateway;
  private final TokenService tokenService;

  AssetServiceClient(AssetGateway assetGateway, TokenService tokenService) {
    this.tokenService = tokenService;
    this.assetGateway = assetGateway;
  }

  @Override
  public AssetUpdateResponse process(AssetRequest assetRequest) {
    return assetGateway.process(tokenService.getBearerToken(), assetRequest);
  }


  @FeignClient(name = "assets",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface AssetGateway {
    @PostMapping(value = "/assets",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    AssetUpdateResponse process(@RequestHeader("Authorization") String bearerToken,
                                AssetRequest assetRequest);
  }
}
