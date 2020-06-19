package com.beancounter.client.services;

import com.beancounter.auth.common.TokenService;
import com.beancounter.client.AssetService;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.model.Asset;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@Service
public class AssetServiceClient implements AssetService {

  private final AssetGateway assetGateway;
  private final TokenService tokenService;

  @Value("${marketdata.url:http://localhost:9510/api}")
  private String marketDataUrl;

  AssetServiceClient(AssetGateway assetGateway, TokenService tokenService) {
    this.tokenService = tokenService;
    this.assetGateway = assetGateway;
  }

  @PostConstruct
  void logConfig() {
    log.info("marketdata.url: {}", marketDataUrl);
  }

  @Override
  public AssetUpdateResponse process(AssetRequest assetRequest) {
    return assetGateway.process(tokenService.getBearerToken(), assetRequest);
  }

  @Override
  @Async
  public void backFillEvents(Asset asset) {
    log.debug("Back fill for {}", asset.getCode());
    assetGateway.backFill(tokenService.getBearerToken(), asset.getId());
  }


  @FeignClient(name = "assets",
      url = "${marketdata.url:http://localhost:9510/api}")
  public interface AssetGateway {
    @PostMapping(value = "/assets",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    AssetUpdateResponse process(@RequestHeader("Authorization") String bearerToken,
                                AssetRequest assetRequest);

    @PostMapping(value = "/assets/{id}/events",
        produces = {MediaType.APPLICATION_JSON_VALUE},
        consumes = {MediaType.APPLICATION_JSON_VALUE})
    void backFill(@RequestHeader("Authorization") String bearerToken,
                  @PathVariable("id") String assetId);

  }


}
