package com.beancounter.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestMarketData {
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void is_MarketDataSerializing() throws Exception {

    Collection<MarketData> marketDataCollection = new ArrayList<>();
    marketDataCollection.add(MarketData.builder()
        .asset(AssetUtils.getJsonAsset("Asset", "Market"))
        .close(BigDecimal.TEN)
        .open(BigDecimal.ONE)
        .close(BigDecimal.TEN)
        .high(BigDecimal.TEN)
        .date("2012-10-01")
        .build());

    PriceResponse priceResponse = PriceResponse.builder().data(marketDataCollection).build();

    PriceResponse fromJson = objectMapper.readValue(
        objectMapper.writeValueAsString(priceResponse),
        PriceResponse.class);

    assertThat(fromJson).isEqualToComparingFieldByField(priceResponse);
  }

  @Test
  void is_QuantitiesWorking() throws Exception {
    QuantityValues quantityValues = QuantityValues.builder().build();
    assertThat(quantityValues)
        .hasFieldOrPropertyWithValue("sold", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("purchased", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("adjustment", BigDecimal.ZERO)
    ;

    assertThat(quantityValues.getTotal()).isEqualTo(BigDecimal.ZERO);
    String json = objectMapper.writeValueAsString(quantityValues);
    assertThat(objectMapper.readValue(json, QuantityValues.class))
        .isEqualToComparingFieldByField(quantityValues);
  }

  @Test
  void is_PriceRequestSerializing() throws Exception {
    Collection<Asset> assets = new ArrayList<>();
    assets.add(AssetUtils.getJsonAsset("ABC", "XYZ"));
    PriceRequest priceRequest = PriceRequest.builder()
        .date("2019-11-11")
        .assets(assets)
        .build();
    String json = objectMapper.writeValueAsString(priceRequest);
    PriceRequest fromJson = objectMapper.readValue(json, PriceRequest.class);
    assertThat(fromJson).isEqualToComparingFieldByField(priceRequest);
  }

}
