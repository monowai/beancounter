package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class TestAsset {
  @Test
  void jsonSerialization() throws Exception {

    Asset asset = Asset.builder()
        .id("SomeId")
        .market(Market.builder().id("NYSE").build())
        .build();

    assertThat(asset).isNotNull();

    ObjectMapper om = new ObjectMapper();
    String json = om.writeValueAsString(asset);

    Asset fromJson = om.readValue(json, Asset.class);

    assertThat(fromJson).isEqualTo(asset);
  }
}
