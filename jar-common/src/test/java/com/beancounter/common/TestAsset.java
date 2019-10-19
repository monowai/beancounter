package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.Test;


class TestAsset {
  @Test
  @VisibleForTesting
  void is_Serializing() throws Exception {

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
  @VisibleForTesting
  void is_AssetKeyParsing() {
    Asset asset = AssetUtils.getAsset("ACODE", "MCODE");

    String keyIn = AssetUtils.toKey(asset);

    assertThat(AssetUtils.fromKey(keyIn)).isEqualTo(asset);

  }

  @Test
  @VisibleForTesting
  void is_AssetKeyExceptionsBeingThrown() {
    assertThrows(BusinessException.class, () -> AssetUtils.toKey(null));
    assertThrows(BusinessException.class, () -> AssetUtils.getAsset("CodeWithNoMarket",
        (Market) null));
    assertThrows(BusinessException.class, () -> AssetUtils.fromKey("CodeWithNoMarket"));
  }

}
