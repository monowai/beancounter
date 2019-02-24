package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.helper.AssetHelper;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class TestAsset {
  @Test
  void jsonSerialization() throws Exception {

    Asset asset = Asset.builder()
        .code("SomeId")
        .name("Some Name")
        .category("Equity")
        .market(Market.builder().code("NYSE").build())
        .build();

    assertThat(asset).isNotNull();

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(asset);

    Asset fromJson = om.readValue(json, Asset.class);

    assertThat(fromJson).isEqualTo(asset);
  }

  @Test
  void parseKeyInAndOut() {
    Asset asset = AssetHelper.getAsset("ACODE", "MCODE");

    String keyIn = AssetHelper.parseKey(asset);

    assertThat(AssetHelper.getAsset(keyIn)).isEqualTo(asset);

  }

  @Test
  void invalidKey() {
    assertThrows(BusinessException.class, () -> AssetHelper.parseKey(null));
    assertThrows(BusinessException.class, () -> AssetHelper.getAsset("CodeWithNoMarket", null));
    assertThrows(BusinessException.class, () -> AssetHelper.getAsset("CodeWithNoMarket"));
  }

}
