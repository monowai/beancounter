package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.AssetSearchResponse;
import com.beancounter.common.contracts.AssetSearchResult;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;


class TestAsset {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void is_PriceRequestForAsset() {
    PriceRequest priceRequest = PriceRequest.of(AssetUtils
        .getAsset("NASDAQ", "EBAY"))
        .date("2019-10-18").build();

    assertThat(priceRequest.getAssets()).hasSize(1);
  }

  @Test
  void is_AssetResponse() throws Exception {
    AssetResponse assetResponse = AssetResponse.builder()
        .data(AssetUtils.getAsset("as", "Blah")).build();
    assetResponse.getData().setMarketCode(null);// JsonIgnore
    String json = objectMapper.writeValueAsString(assetResponse);
    AssetResponse fromJson = objectMapper.readValue(json, AssetResponse.class);

    assertThat(fromJson).isEqualToComparingFieldByField(assetResponse);
  }

  @Test
  void assetRequestSerializes() throws Exception {
    Asset asset = AssetUtils.getJsonAsset("BBB", "AAA");

    AssetRequest assetRequest = AssetRequest
        .builder()
        .data(AssetUtils.toKey(asset), AssetUtils.getAssetInput("BBB", "AAA"))
        .data("second", AssetUtils.getAssetInput("Whee", "Twee"))
        .build();

    assertThat(assetRequest.getData())
        .containsKeys(AssetUtils.toKey(asset));
    String json = objectMapper.writeValueAsString(assetRequest);

    AssetRequest fromJson = objectMapper.readValue(json, AssetRequest.class);

    assertThat(fromJson).isEqualTo(assetRequest);
  }

  @Test
  void assetResponseSerializes() throws Exception {
    Asset asset = AssetUtils.getJsonAsset("BBB", "AAA");

    AssetUpdateResponse assetUpdateResponse = AssetUpdateResponse
        .builder()
        .data(AssetUtils.toKey(asset), asset)
        .data("second", AssetUtils.getJsonAsset("Whee", "Twee"))
        .build();

    assertThat(assetUpdateResponse.getData()).containsKeys(AssetUtils.toKey(asset));

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(assetUpdateResponse);

    AssetUpdateResponse fromJson = om.readValue(json, AssetUpdateResponse.class);

    assertThat(fromJson).isEqualTo(assetUpdateResponse);
  }

  @Test
  void is_AssetKeyParsing() {
    Asset asset = AssetUtils.getAsset(
        "MCODE",
        "ACODE");

    String keyIn = AssetUtils.toKey(asset);

    assertThat(AssetUtils.toKey(AssetInput.builder()
        .market("MCODE")
        .code("ACODE").build()))
        .isEqualTo(keyIn);

    AssetInput assetInput = AssetUtils.getAssetInput(asset);
    assertThat(assetInput)
        .hasFieldOrProperty("code")
        .hasFieldOrProperty("market")
        .hasFieldOrProperty("resolvedAsset");

    assertThat(AssetUtils.toKey(assetInput))
        .isEqualTo(keyIn);

    assertThat(AssetUtils.fromKey(keyIn)).isEqualTo(asset);
  }

  @SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
  @Test
  void is_AssetKeyExceptionsBeingThrown() {

    assertThrows(BusinessException.class,
        () -> AssetUtils.fromKey("CodeWithNoMarket"));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.fromKey(null));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.getAsset((Market) null, null));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.getAsset((Market) null, "Twee"));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.getAsset((String) null, "Twee"));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.getAsset((Market) null, null));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.toKey((Asset) null));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.toKey((AssetInput) null));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.toKey("Twee", null));
    assertThrows(NullPointerException.class,
        () -> AssetUtils.toKey(null, null));
  }

  @Test
  void is_AssetsSplitByMarket() {
    Collection<AssetInput> assets = new ArrayList<>();
    assets.add(AssetInput.builder().resolvedAsset(
        AssetUtils.getAsset("AAA", "ABC")).build());
    assets.add(AssetInput.builder().resolvedAsset(
        AssetUtils.getAsset("AAA", "123")).build());
    assets.add(AssetInput.builder().resolvedAsset(
        AssetUtils.getAsset("BBB", "ABC")).build());
    assets.add(AssetInput.builder().resolvedAsset(
        AssetUtils.getAsset("BBB", "123")).build());
    assets.add(AssetInput.builder().resolvedAsset(
        AssetUtils.getAsset("CCC", "123")).build());
    Map<String, Collection<Asset>> results = AssetUtils.split(assets);
    assertThat(results.size()).isEqualTo(3);
    assertThat(results.get("AAA")).hasSize(2);
    assertThat(results.get("BBB")).hasSize(2);
    assertThat(results.get("CCC")).hasSize(1);

  }

  @Test
  void is_SearchResponse() throws JsonProcessingException {
    AssetSearchResult searchResult = AssetSearchResult.builder()
        .type("Non Default")
        .name("Some Name")
        .symbol("Some Symbol")
        .region("Some Region")
        .build();

    AssetSearchResult withDefaults = AssetSearchResult.builder()
        .name("Name")
        .symbol("Symbol")
        .region("Region")
        .build();

    Collection<AssetSearchResult>results = new ArrayList<>();
    results.add(searchResult);
    results.add(withDefaults);
    AssetSearchResponse searchResponse = AssetSearchResponse.builder()
        .data(results)
        .build();

    String json = objectMapper.writeValueAsString(searchResponse);
    AssetSearchResponse fromJson = objectMapper.readValue(json, AssetSearchResponse.class);
    assertThat(fromJson)
        .isEqualToComparingFieldByField(searchResponse)
        .hasNoNullFieldsOrProperties();

    assertThat(fromJson.getData()).isNotEmpty();
    assertThat(fromJson.getData().iterator().next().getType()).isNotNull();

  }

}
