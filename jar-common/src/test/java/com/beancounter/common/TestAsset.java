package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;


class TestAsset {
  @Test
  void assetRequestSerializes() throws Exception {
    Asset asset = AssetUtils.getJsonAsset("AAA", "BBB");

    AssetRequest assetRequest = AssetRequest
        .builder()
        .asset(AssetUtils.toKey(asset), asset)
        .asset("second", AssetUtils.getJsonAsset("Twee", "Whee"))
        .build();

    assertThat(assetRequest.getAssets()).containsKeys(AssetUtils.toKey(asset));

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(assetRequest);

    AssetRequest fromJson = om.readValue(json, AssetRequest.class);

    assertThat(fromJson).isEqualTo(assetRequest);
  }

  @Test
  void assetResponseSerializes() throws Exception {
    Asset asset = AssetUtils.getJsonAsset("AAA", "BBB");

    AssetResponse assetResponse = AssetResponse
        .builder()
        .asset(AssetUtils.toKey(asset), asset)
        .asset("second", AssetUtils.getJsonAsset("Twee", "Whee"))
        .build();

    assertThat(assetResponse.getAssets()).containsKeys(AssetUtils.toKey(asset));

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(assetResponse);

    AssetResponse fromJson = om.readValue(json, AssetResponse.class);

    assertThat(fromJson).isEqualTo(assetResponse);
  }

  @Test
  void is_AssetKeyParsing() {
    Asset asset = AssetUtils.getAsset("ACODE", "MCODE");

    String keyIn = AssetUtils.toKey(asset);

    assertThat(AssetUtils.fromKey(keyIn)).isEqualTo(asset);

  }

  @Test
  void is_AssetKeyExceptionsBeingThrown() {
    assertThrows(NullPointerException.class, () -> AssetUtils.toKey(null));
    assertThrows(NullPointerException.class, () -> AssetUtils.getAsset("CodeWithNoMarket",
        (Market) null));
    assertThrows(BusinessException.class, () -> AssetUtils.fromKey("CodeWithNoMarket"));
  }

  @Test
  void is_AssetsSplitByMarket() {
    Collection<Asset> assets = new ArrayList<>();
    assets.add(AssetUtils.getAsset("ABC", "AAA"));
    assets.add(AssetUtils.getAsset("123", "AAA"));
    assets.add(AssetUtils.getAsset("ABC", "BBB"));
    assets.add(AssetUtils.getAsset("123", "BBB"));
    assets.add(AssetUtils.getAsset("123", "CCC"));
    Map<String, Collection<Asset>> results = AssetUtils.split(assets);
    assertThat(results.size()).isEqualTo(3);
    assertThat(results.get("AAA")).hasSize(2);
    assertThat(results.get("BBB")).hasSize(2);
    assertThat(results.get("CCC")).hasSize(1);

  }
}
